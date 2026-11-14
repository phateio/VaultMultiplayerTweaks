package io.github.phateio.vaultMultiplayerTweaks;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * What {@link VaultCooldowns} needs from one vault block: the plugin's cooldown ledger (stored in the
 * vault's PDC) and the vanilla "rewarded players" set.
 */
interface VaultStore {

    /** Whether this vault has been taken over by the plugin (ledger key present in PDC). */
    boolean hasLedger();

    /** Ledger entries in stored order; empty when there is no ledger. */
    List<PlayerVaultData> entries();

    /** Earliest time (epoch millis) at which some entry may have expired, or null when nothing is pending. */
    @Nullable Long nextRefresh();

    /** Persist the ledger; a null {@code nextRefresh} clears it. */
    void save(List<PlayerVaultData> entries, @Nullable Long nextRefresh);

    /** Snapshot of the vanilla rewarded-players set. */
    Collection<UUID> rewardedPlayers();

    /** Remove one player from the vanilla rewarded-players set, letting them open the vault again. */
    void unreward(UUID player);
}
