package io.github.phateio.vaultMultiplayerTweaks;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Vault;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class VaultMultiplayerTweaks extends JavaPlugin implements Listener {

    private final NamespacedKey KEY_COOLDOWN = NamespacedKey.fromString("rewarded_cooldown", this);
    private final NamespacedKey KEY_NEXT = NamespacedKey.fromString("refresh_next", this);
    private final ListPersistentDataType<byte[], PlayerVaultData> listPVDType = PersistentDataType.LIST.listTypeFrom(PlayerVaultData.TYPE);

    private VaultCooldowns cooldowns;

    @Override
    public void onEnable() {
        FileConfiguration config = getConfig();
        config.addDefault("vault_cooldown_sec", 86400);
        config.options().copyDefaults(true);
        saveConfig();

        cooldowns = new VaultCooldowns(config.getLong("vault_cooldown_sec") * 1000);

        getServer().getPluginManager().registerEvents(this, this);
    }

    // MONITOR: record the cooldown only once no other plugin can still cancel the loot.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVaultDropLoot(BlockDispenseLootEvent e) {
        // getState(false): operate on the live block entity, not a copy.
        if (!(e.getBlock().getState(false) instanceof Vault v)) return;
        if (e.getPlayer() == null) return;

        cooldowns.onLootDispensed(new BukkitVaultStore(v), e.getPlayer().getUniqueId(), System.currentTimeMillis());
        v.update();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerRightClickVault(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getClickedBlock() == null) return;
        if (!(e.getClickedBlock().getState(false) instanceof Vault v)) return;

        if (cooldowns.onInteract(new BukkitVaultStore(v), System.currentTimeMillis())) v.update();
    }

    /** {@link VaultStore} backed by a live (non-snapshot) vault block state. */
    private final class BukkitVaultStore implements VaultStore {
        private final Vault vault;
        private final PersistentDataContainer pdc;

        BukkitVaultStore(Vault vault) {
            this.vault = vault;
            this.pdc = vault.getPersistentDataContainer();
        }

        @Override
        public boolean hasLedger() {
            return pdc.has(KEY_COOLDOWN);
        }

        @Override
        public List<PlayerVaultData> entries() {
            return pdc.getOrDefault(KEY_COOLDOWN, listPVDType, List.of());
        }

        @Override
        public @Nullable Long nextRefresh() {
            return pdc.get(KEY_NEXT, PersistentDataType.LONG);
        }

        @Override
        public void save(List<PlayerVaultData> entries, @Nullable Long nextRefresh) {
            pdc.set(KEY_COOLDOWN, listPVDType, entries);
            if (nextRefresh == null) pdc.remove(KEY_NEXT);
            else pdc.set(KEY_NEXT, PersistentDataType.LONG, nextRefresh);
        }

        @Override
        public Collection<UUID> rewardedPlayers() {
            return vault.getRewardedPlayers();
        }

        @Override
        public void unreward(UUID player) {
            vault.removeRewardedPlayer(player);
        }
    }
}
