package io.github.phateio.vaultMultiplayerTweaks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Behaviour carried over from v1. */
class VaultCooldownsTest {
    static final long COOLDOWN = 1_000;
    final VaultCooldowns rules = new VaultCooldowns(COOLDOWN);
    final FakeVaultStore vault = new FakeVaultStore();
    final UUID alice = new UUID(0, 1), bob = new UUID(0, 2);

    /** Loot event then vanilla's addToRewardedPlayers, without going through the state machine. */
    void claim(UUID player, long now) {
        rules.onLootDispensed(vault, player, now);
        vault.rewarded.add(player);
    }

    @Test
    void firstInteractTakesOverVaultAndForgetsVanillaRewards() {
        vault.rewarded.add(alice); // claimed before the plugin was installed
        assertTrue(rules.onInteract(vault, 0));
        assertTrue(vault.hasLedger());
        assertEquals(List.of(), vault.entries);
        assertNull(vault.nextRefresh);
        assertFalse(vault.rewarded.contains(alice));
    }

    @Test
    void lootOnUnmanagedVaultForgetsOldRewardsButRecordsClaimer() {
        vault.rewarded.add(bob);
        claim(alice, 0);
        assertFalse(vault.rewarded.contains(bob));
        assertTrue(vault.rewarded.contains(alice));
        assertEquals(List.of(new PlayerVaultData(alice, COOLDOWN)), vault.entries);
        assertEquals(COOLDOWN, vault.nextRefresh);
    }

    @Test
    void cannotReclaimBeforeCooldownEnds() {
        claim(alice, 0);
        assertFalse(rules.onInteract(vault, COOLDOWN - 1));
        assertTrue(vault.rewarded.contains(alice));
    }

    @Test
    void cooldownExpiryLetsPlayerClaimAgain() {
        claim(alice, 0);
        claim(bob, 500);
        assertTrue(rules.onInteract(vault, COOLDOWN));
        assertFalse(vault.rewarded.contains(alice));
        assertTrue(vault.rewarded.contains(bob));
        assertEquals(List.of(new PlayerVaultData(bob, 500 + COOLDOWN)), vault.entries);
        assertEquals(500 + COOLDOWN, vault.nextRefresh);
    }

    @Test
    void lastExpiryClearsNextRefresh() {
        claim(alice, 0);
        rules.onInteract(vault, COOLDOWN);
        assertEquals(List.of(), vault.entries);
        assertNull(vault.nextRefresh);
        assertFalse(rules.onInteract(vault, 10 * COOLDOWN)); // nothing pending: no write
    }

    @Test
    void lootAfterExpiryAlsoExpiresOthers() {
        claim(alice, 0);
        claim(bob, 2 * COOLDOWN);
        assertFalse(vault.rewarded.contains(alice));
        assertEquals(List.of(new PlayerVaultData(bob, 3 * COOLDOWN)), vault.entries);
    }

    /** Alone at the vault: claim, wait out the cooldown, claim again (through the vanilla state model). */
    @Test
    void aloneAtVaultFullCycle() {
        vault.nearby.add(alice);
        vault.tick();
        assertTrue(vault.insertKey(rules, alice, 0)); // first click takes the vault over, key accepted
        vault.tick();
        assertEquals(FakeVaultStore.State.INACTIVE, vault.state); // alice rewarded, nobody else around

        assertFalse(vault.insertKey(rules, alice, COOLDOWN - 1));
        // Expired: this click releases alice, but vanilla still refuses (vault INACTIVE until next tick).
        assertFalse(vault.insertKey(rules, alice, COOLDOWN));
        vault.tick();
        assertEquals(FakeVaultStore.State.ACTIVE, vault.state);
        assertTrue(vault.insertKey(rules, alice, COOLDOWN + 50));
    }
}
