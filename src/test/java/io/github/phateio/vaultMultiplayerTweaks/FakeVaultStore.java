package io.github.phateio.vaultMultiplayerTweaks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** In-memory vault: PDC ledger + vanilla rewarded set. Mimics vanilla's rewarded bookkeeping. */
final class FakeVaultStore implements VaultStore {
    List<PlayerVaultData> entries = null; // null = no ledger key in PDC
    Long nextRefresh = null;
    final Set<UUID> rewarded = new LinkedHashSet<>();

    @Override public boolean hasLedger() { return entries != null; }
    @Override public List<PlayerVaultData> entries() { return entries == null ? List.of() : List.copyOf(entries); }
    @Override public Long nextRefresh() { return nextRefresh; }
    @Override public void save(List<PlayerVaultData> entries, Long nextRefresh) {
        this.entries = new ArrayList<>(entries);
        this.nextRefresh = nextRefresh;
    }
    @Override public Collection<UUID> rewardedPlayers() { return Set.copyOf(rewarded); }
    @Override public void unreward(UUID player) { rewarded.remove(player); }

    /** Vanilla flow for a successful key insert: loot event first, then addToRewardedPlayers. */
    void claim(VaultCooldowns c, UUID player, long now) {
        c.onLootDispensed(this, player, now);
        rewarded.add(player);
    }

    /** Vanilla's check in tryInsertKey after the plugin's PlayerInteractEvent handler has run. */
    boolean canClaim(VaultCooldowns c, UUID player, boolean inactive, long now) {
        c.onInteract(this, inactive, now);
        return !inactive && !rewarded.contains(player);
    }
}
