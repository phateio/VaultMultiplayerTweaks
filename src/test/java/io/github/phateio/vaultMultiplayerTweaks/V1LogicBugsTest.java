package io.github.phateio.vaultMultiplayerTweaks;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Bugs inherited from v1 (red on the original logic). */
class V1LogicBugsTest {
    static final long CD = 1_000;
    final FakeVaultStore v = new FakeVaultStore();
    final UUID alice = new UUID(0, 1), bob = new UUID(0, 2);

    /** A: someone else keeps the vault ACTIVE; alice's cooldown has ended but she is still refused. */
    @Test
    void expiredPlayerCanClaimWhileVaultIsActive() {
        VaultCooldowns c = new VaultCooldowns(CD);
        v.claim(c, alice, 0);
        assertTrue(v.canClaim(c, alice, false, CD + 1));
    }

    /** B1: a second ledger entry for the same player (e.g. loot recorded, then cancelled by a later plugin). */
    @Test
    void reclaimReplacesOldEntryInsteadOfDuplicating() {
        VaultCooldowns c = new VaultCooldowns(CD);
        c.onLootDispensed(v, alice, 0);   // recorded, but another plugin cancelled: never rewarded
        v.claim(c, alice, 500);           // real claim, cooldown until 1500
        assertEquals(List.of(new PlayerVaultData(alice, 500 + CD)), v.entries);
        c.onInteract(v, CD);        // stale entry due: must not unlock alice early
        assertTrue(v.rewarded.contains(alice));
    }

    /** B2: cooldown shortened in config; a newer, shorter entry sits behind an older, longer one. */
    @Test
    void shorterCooldownAfterConfigChangeIsNotBlockedByOlderEntry() {
        new VaultCooldowns(10 * CD).onLootDispensed(v, alice, 0); // until 10000
        v.rewarded.add(alice);
        VaultCooldowns shorter = new VaultCooldowns(CD);
        v.claim(shorter, bob, 100);                                 // until 1100
        assertEquals(100 + CD, v.nextRefresh);
        assertTrue(shorter.onInteract(v, 100 + CD));
        assertFalse(v.rewarded.contains(bob));
        assertTrue(v.rewarded.contains(alice));
    }

    /** C: only record a cooldown once no other plugin can cancel the loot. */
    @Test
    void lootListenerRunsAtMonitor() throws Exception {
        EventHandler h = VaultMultiplayerTweaks.class
                .getMethod("onVaultDropLoot", BlockDispenseLootEvent.class).getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, h.priority());
        assertTrue(h.ignoreCancelled());
    }
}
