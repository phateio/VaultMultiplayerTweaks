package io.github.phateio.vaultMultiplayerTweaks;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player vault cooldown rules, free of Bukkit so they can be unit tested.
 * <p>
 * The ledger holds at most one entry per player: when that player may open the vault again.
 * Times are epoch milliseconds (wall clock, so the cooldown keeps running while the server is down).
 */
final class VaultCooldowns {

    private final long cooldownMillis;

    VaultCooldowns(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    /**
     * A player was just given loot by this vault (vanilla marks them rewarded right after).
     * Called only for loot that no plugin cancelled.
     */
    void onLootDispensed(VaultStore vault, UUID player, long now) {
        List<PlayerVaultData> entries = takeOverIfNeeded(vault)
                ? new ArrayList<>()
                : releaseDue(vault, now, vault.entries());

        entries.removeIf(e -> e.uuid().equals(player)); // the latest claim wins
        entries.add(new PlayerVaultData(player, now + cooldownMillis));
        save(vault, entries);
    }

    /**
     * A player right-clicked the vault, before vanilla handles the key. Runs in any vault state, so an
     * expired player is released even while other players keep the vault ACTIVE.
     *
     * @return whether the vault was modified (caller must flush it)
     */
    boolean onInteract(VaultStore vault, long now) {
        if (takeOverIfNeeded(vault)) {
            save(vault, List.of());
            return true;
        }
        if (!isDue(vault, now)) return false;

        save(vault, releaseDue(vault, now, vault.entries()));
        return true;
    }

    /**
     * A vault the plugin has never touched still carries vanilla's permanent "already rewarded" list;
     * forget it so those players fall under the cooldown rules from now on.
     *
     * @return whether the vault was just taken over (it has no ledger yet)
     */
    private static boolean takeOverIfNeeded(VaultStore vault) {
        if (vault.hasLedger()) return false;
        for (UUID uuid : vault.rewardedPlayers()) vault.unreward(uuid);
        return true;
    }

    private static boolean isDue(VaultStore vault, long now) {
        Long next = vault.nextRefresh();
        return next != null && next <= now;
    }

    /**
     * Release every player whose cooldown has ended and return the remaining entries.
     * Entries need not be sorted (the cooldown may change between claims), and duplicate entries
     * left by v1 collapse to the latest one first, so a stale entry never releases a player early.
     */
    private static List<PlayerVaultData> releaseDue(VaultStore vault, long now, List<PlayerVaultData> entries) {
        Map<UUID, PlayerVaultData> latest = new LinkedHashMap<>();
        for (PlayerVaultData e : entries) latest.merge(e.uuid(), e, (a, b) -> a.next() >= b.next() ? a : b);

        List<PlayerVaultData> remaining = new ArrayList<>(latest.size());
        for (PlayerVaultData e : latest.values()) {
            if (e.next() > now) remaining.add(e);
            else vault.unreward(e.uuid());
        }
        return remaining;
    }

    private static void save(VaultStore vault, List<PlayerVaultData> entries) {
        vault.save(entries, earliest(entries));
    }

    private static @Nullable Long earliest(List<PlayerVaultData> entries) {
        return entries.stream().map(PlayerVaultData::next).min(Long::compare).orElse(null);
    }
}
