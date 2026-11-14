package io.github.phateio.vaultMultiplayerTweaks;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Per-player vault cooldown rules, free of Bukkit so they can be unit tested.
 * Times are epoch milliseconds.
 */
final class VaultCooldowns {

    private final long cooldownMillis;

    VaultCooldowns(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    /** A player was just given loot by this vault (vanilla is about to mark them rewarded). */
    void onLootDispensed(VaultStore vault, UUID player, long now) {
        if (!vault.hasLedger()) unrewardAll(vault);

        List<PlayerVaultData> entries = new ArrayList<>(vault.entries());
        Long next = vault.nextRefresh();
        if (next != null && next <= now) entries = expire(vault, now, entries);

        entries.removeIf(e -> e.uuid().equals(player)); // one entry per player: the latest claim wins
        entries.add(new PlayerVaultData(player, now + cooldownMillis));
        vault.save(entries, earliest(entries));
    }

    /**
     * A player right-clicked the vault, before vanilla handles the key. Runs in any vault state, so an
     * expired player is released even while other players keep the vault ACTIVE.
     *
     * @return whether the vault was modified (caller must flush it)
     */
    boolean onInteract(VaultStore vault, long now) {
        if (!vault.hasLedger()) { // vault not yet managed by the plugin
            unrewardAll(vault);
            vault.save(Collections.emptyList(), null);
            return true;
        }

        Long next = vault.nextRefresh();
        if (next == null || next > now) return false;

        List<PlayerVaultData> entries = expire(vault, now, new ArrayList<>(vault.entries()));
        vault.save(entries, earliest(entries));
        return true;
    }

    private static void unrewardAll(VaultStore vault) {
        for (UUID uuid : vault.rewardedPlayers()) vault.unreward(uuid);
    }

    /** Release every due entry; entries need not be sorted (cooldown may change between claims). */
    private static List<PlayerVaultData> expire(VaultStore vault, long now, List<PlayerVaultData> entries) {
        List<PlayerVaultData> remaining = new ArrayList<>(entries.size());
        for (PlayerVaultData pvd : entries) {
            if (pvd.next() > now) remaining.add(pvd);
            else vault.unreward(pvd.uuid());
        }
        return remaining;
    }

    private static @Nullable Long earliest(List<PlayerVaultData> entries) {
        return entries.stream().map(PlayerVaultData::next).min(Long::compare).orElse(null);
    }
}
