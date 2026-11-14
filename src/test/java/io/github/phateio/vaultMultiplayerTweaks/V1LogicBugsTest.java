package io.github.phateio.vaultMultiplayerTweaks;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Bugs inherited from v1 (each red on the v1 logic). */
class V1LogicBugsTest {
    static final long COOLDOWN = 1_000;
    final FakeVaultStore vault = new FakeVaultStore();
    final UUID alice = new UUID(0, 1), bob = new UUID(0, 2);

    /** A: bob (not yet rewarded) keeps the vault ACTIVE; alice's cooldown has ended, she must get in. */
    @Test
    void expiredPlayerCanClaimWhileVaultIsActive() {
        VaultCooldowns rules = new VaultCooldowns(COOLDOWN);
        vault.nearby.add(alice);
        vault.tick();
        assertTrue(vault.insertKey(rules, alice, 0));
        vault.nearby.add(bob);
        vault.tick();
        assertEquals(FakeVaultStore.State.ACTIVE, vault.state);
        assertTrue(vault.insertKey(rules, alice, COOLDOWN + 1));
    }

    /** B1: the same player claims again (e.g. after vanilla's 128-player cap evicted them). */
    @Test
    void reclaimReplacesOldEntryInsteadOfDuplicating() {
        VaultCooldowns rules = new VaultCooldowns(COOLDOWN);
        rules.onLootDispensed(vault, alice, 0);
        rules.onLootDispensed(vault, alice, 500);
        vault.rewarded.add(alice);
        assertEquals(List.of(new PlayerVaultData(alice, 500 + COOLDOWN)), vault.entries);
        rules.onInteract(vault, COOLDOWN); // the replaced entry's time: must not release alice
        assertTrue(vault.rewarded.contains(alice));
    }

    /** B2: a ledger written by v1 may already hold duplicates; the stale one must not release early. */
    @Test
    void staleDuplicateFromV1DoesNotReleaseEarly() {
        VaultCooldowns rules = new VaultCooldowns(COOLDOWN);
        vault.entries = new ArrayList<>(List.of(new PlayerVaultData(alice, COOLDOWN), new PlayerVaultData(alice, COOLDOWN + 500)));
        vault.nextRefresh = COOLDOWN;
        vault.rewarded.add(alice);
        rules.onInteract(vault, COOLDOWN);
        assertTrue(vault.rewarded.contains(alice));
        assertEquals(List.of(new PlayerVaultData(alice, COOLDOWN + 500)), vault.entries);
        assertEquals(COOLDOWN + 500, vault.nextRefresh);
    }

    /** B3: cooldown shortened in config; a newer, shorter entry sits behind an older, longer one. */
    @Test
    void shorterCooldownAfterConfigChangeIsNotBlockedByOlderEntry() {
        new VaultCooldowns(10 * COOLDOWN).onLootDispensed(vault, alice, 0); // until 10000
        vault.rewarded.add(alice);
        VaultCooldowns shorter = new VaultCooldowns(COOLDOWN);
        shorter.onLootDispensed(vault, bob, 100);                           // until 1100
        vault.rewarded.add(bob);
        assertEquals(100 + COOLDOWN, vault.nextRefresh);
        assertTrue(shorter.onInteract(vault, 100 + COOLDOWN));
        assertFalse(vault.rewarded.contains(bob));
        assertTrue(vault.rewarded.contains(alice));
    }

    /**
     * C: only record a cooldown once no other plugin can cancel the loot. Pins the declaration only:
     * Bukkit's priority ordering itself is not exercised here.
     */
    @Test
    void lootListenerRunsAtMonitor() throws Exception {
        EventHandler h = VaultMultiplayerTweaks.class
                .getMethod("onVaultDropLoot", BlockDispenseLootEvent.class).getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, h.priority());
        assertTrue(h.ignoreCancelled());
    }
}
