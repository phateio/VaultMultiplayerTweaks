package io.github.phateio.vaultMultiplayerTweaks;

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

        entries.add(new PlayerVaultData(player, now + cooldownMillis));
        vault.save(entries, entries.getFirst().next());
    }

    /**
     * A player right-clicked the vault, before vanilla handles the key.
     *
     * @param inactive whether the vault block is currently in the INACTIVE state
     * @return whether the vault was modified (caller must flush it)
     */
    boolean onInteract(VaultStore vault, boolean inactive, long now) {
        if (!inactive) return false;

        if (!vault.hasLedger()) { // vault not yet managed by the plugin
            unrewardAll(vault);
            vault.save(Collections.emptyList(), null);
            return true;
        }

        Long next = vault.nextRefresh();
        if (next == null || next > now) return false;

        List<PlayerVaultData> entries = expire(vault, now, new ArrayList<>(vault.entries()));
        vault.save(entries, entries.isEmpty() ? null : entries.getFirst().next());
        return true;
    }

    private static void unrewardAll(VaultStore vault) {
        for (UUID uuid : vault.rewardedPlayers()) vault.unreward(uuid);
    }

    private static List<PlayerVaultData> expire(VaultStore vault, long now, List<PlayerVaultData> entries) {
        int idx = 0;
        for (; idx < entries.size(); idx++) {
            PlayerVaultData pvd = entries.get(idx);
            if (pvd.next() > now) break;
            vault.unreward(pvd.uuid());
        }
        return new ArrayList<>(entries.subList(idx, entries.size()));
    }
}
