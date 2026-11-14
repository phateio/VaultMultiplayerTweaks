package io.github.phateio.vaultMultiplayerTweaks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory vault: the plugin's PDC ledger plus a model of vanilla's vault (Leaves/Paper 1.21.11):
 * <ul>
 *   <li>state is re-evaluated on a later tick, not instantly: ACTIVE iff some nearby player is not
 *       rewarded ({@code VaultState.updateStateForConnectedPlayers});</li>
 *   <li>a key is accepted only while ACTIVE and only from a non-rewarded player
 *       ({@code VaultBlockEntity.Server.tryInsertKey});</li>
 *   <li>the loot event fires before the player is added to the rewarded set.</li>
 * </ul>
 */
final class FakeVaultStore implements VaultStore {
    enum State { INACTIVE, ACTIVE, UNLOCKING }

    List<PlayerVaultData> entries = null; // null = no ledger key in PDC
    Long nextRefresh = null;
    final Set<UUID> rewarded = new LinkedHashSet<>();
    final Set<UUID> nearby = new LinkedHashSet<>();
    State state = State.INACTIVE;

    @Override public boolean hasLedger() { return entries != null; }
    @Override public List<PlayerVaultData> entries() { return entries == null ? List.of() : List.copyOf(entries); }
    @Override public Long nextRefresh() { return nextRefresh; }
    @Override public void save(List<PlayerVaultData> entries, Long nextRefresh) {
        this.entries = new ArrayList<>(entries);
        this.nextRefresh = nextRefresh;
    }
    @Override public Collection<UUID> rewardedPlayers() { return List.copyOf(rewarded); }
    @Override public void unreward(UUID player) { rewarded.remove(player); }

    /** Vanilla's periodic state update (also what ends an ejection). */
    void tick() {
        state = nearby.stream().anyMatch(p -> !rewarded.contains(p)) ? State.ACTIVE : State.INACTIVE;
    }

    /** Right-click with a key: the plugin's PlayerInteractEvent handler, then vanilla's tryInsertKey. */
    boolean insertKey(VaultCooldowns rules, UUID player, long now) {
        rules.onInteract(this, now);
        if (state != State.ACTIVE || rewarded.contains(player)) return false;
        rules.onLootDispensed(this, player, now); // BlockDispenseLootEvent (not cancelled)
        rewarded.add(player);                     // addToRewardedPlayers
        state = State.UNLOCKING;
        return true;
    }
}
