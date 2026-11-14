package io.github.phateio.vaultMultiplayerTweaks;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Behaviour carried over from v1; must stay green before and after the v1 logic fixes. */
class VaultCooldownsTest {
    static final long CD = 1_000;
    final VaultCooldowns c = new VaultCooldowns(CD);
    final FakeVaultStore v = new FakeVaultStore();
    final UUID alice = new UUID(0, 1), bob = new UUID(0, 2);

    @Test
    void firstInteractTakesOverVaultAndForgetsVanillaRewards() {
        v.rewarded.add(alice); // claimed before the plugin was installed
        assertTrue(c.onInteract(v, true, 0));
        assertTrue(v.hasLedger());
        assertEquals(List.of(), v.entries);
        assertNull(v.nextRefresh);
        assertFalse(v.rewarded.contains(alice));
    }

    @Test
    void lootOnUnmanagedVaultForgetsOldRewardsButRecordsClaimer() {
        v.rewarded.add(bob);
        v.claim(c, alice, 0);
        assertFalse(v.rewarded.contains(bob));
        assertTrue(v.rewarded.contains(alice));
        assertEquals(List.of(new PlayerVaultData(alice, CD)), v.entries);
        assertEquals(CD, v.nextRefresh);
    }

    @Test
    void cannotReclaimBeforeCooldownEnds() {
        v.claim(c, alice, 0);
        assertFalse(c.onInteract(v, true, CD - 1));
        assertTrue(v.rewarded.contains(alice));
    }

    @Test
    void cooldownExpiryLetsPlayerClaimAgain() {
        v.claim(c, alice, 0);
        v.claim(c, bob, 500);
        assertTrue(c.onInteract(v, true, CD));
        assertFalse(v.rewarded.contains(alice));
        assertTrue(v.rewarded.contains(bob));
        assertEquals(List.of(new PlayerVaultData(bob, 500 + CD)), v.entries);
        assertEquals(500 + CD, v.nextRefresh);
    }

    @Test
    void lastExpiryClearsNextRefresh() {
        v.claim(c, alice, 0);
        c.onInteract(v, true, CD);
        assertEquals(List.of(), v.entries);
        assertNull(v.nextRefresh);
        assertFalse(c.onInteract(v, true, 10 * CD)); // nothing pending: no write
    }

    @Test
    void lootAfterExpiryAlsoExpiresOthers() {
        v.claim(c, alice, 0);
        v.claim(c, bob, 2 * CD);
        assertFalse(v.rewarded.contains(alice));
        assertEquals(List.of(new PlayerVaultData(bob, 3 * CD)), v.entries);
    }

    @Test
    void fullCycleInactiveVault() {
        // Alone at the vault: after claiming, the vault turns INACTIVE for alice.
        v.claim(c, alice, 0);
        assertFalse(v.canClaim(c, alice, true, CD - 1));
        // Expired: first click un-rewards (vanilla still refuses: INACTIVE), vault lights up, second click works.
        assertFalse(v.canClaim(c, alice, true, CD));
        assertTrue(v.canClaim(c, alice, false, CD + 50));
    }
}
