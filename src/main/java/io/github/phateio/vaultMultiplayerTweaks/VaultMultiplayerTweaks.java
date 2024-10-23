package io.github.phateio.vaultMultiplayerTweaks;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Vault;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.github.phateio.vaultMultiplayerTweaks.nms.VaultAccess.getRewardedPlayers;
import static io.github.phateio.vaultMultiplayerTweaks.nms.VaultAccess.removePlayer;
import static org.bukkit.block.data.type.Vault.State;

public final class VaultMultiplayerTweaks extends JavaPlugin implements Listener {

    private long CONFIG_VAULT_COOLDOWN;
    private final NamespacedKey KEY_COOLDOWN = NamespacedKey.fromString("rewarded_cooldown", this);
    private final NamespacedKey KEY_NEXT = NamespacedKey.fromString("refresh_next", this);
    private final ListPersistentDataType<byte[], PlayerVaultData> listPVDType = PersistentDataType.LIST.listTypeFrom(PlayerVaultData.TYPE);


    @Override
    public void onEnable() {
        FileConfiguration config = getConfig();
        config.addDefault("vault_cooldown_sec", 86400);
        config.options().copyDefaults(true);
        saveConfig();

        CONFIG_VAULT_COOLDOWN = config.getLong("vault_cooldown_sec");

        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVaultDropLoot(BlockDispenseLootEvent e) {
        if (!(e.getBlock().getState(false) instanceof Vault v)) return;

        var pdc = v.getPersistentDataContainer();

        if (!pdc.has(KEY_COOLDOWN)) removePlayer(v, getRewardedPlayers(v));

        final var now = System.currentTimeMillis();
        List<PlayerVaultData> playerList = new ArrayList<>(pdc.getOrDefault(KEY_COOLDOWN, listPVDType, new ArrayList<>()));

        var nextUpdate = pdc.get(KEY_NEXT, PersistentDataType.LONG);
        if (nextUpdate != null && nextUpdate <= now) {
            playerList = refreshVault(v, now, playerList);
        }

        playerList.add(new PlayerVaultData(e.getPlayer().getUniqueId(), now + CONFIG_VAULT_COOLDOWN * 1000));
        nextUpdate = playerList.getFirst().next();

        pdc.set(KEY_COOLDOWN, listPVDType, playerList);
        pdc.set(KEY_NEXT, PersistentDataType.LONG, nextUpdate);

        v.update();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerRightClickVault(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getClickedBlock().getState(false) instanceof Vault v)) return;
        if (!(v.getBlockData() instanceof org.bukkit.block.data.type.Vault vState)
                || vState.getTrialSpawnerState() != State.INACTIVE) return;

        var pdc = v.getPersistentDataContainer();

        if (!pdc.has(KEY_COOLDOWN)) { // new Vault
            removePlayer(v, getRewardedPlayers(v));
            pdc.set(KEY_COOLDOWN, listPVDType, Collections.emptyList());
            return;
        }

        var nextUpdate = pdc.get(KEY_NEXT, PersistentDataType.LONG);
        if (nextUpdate == null) return;
        final var now = System.currentTimeMillis();
        if (nextUpdate > now) return;

        List<PlayerVaultData> playerList = new ArrayList<>(pdc.getOrDefault(KEY_COOLDOWN, listPVDType, new ArrayList<>()));
        playerList = refreshVault(v, now, playerList);

        if (playerList.isEmpty()) pdc.remove(KEY_NEXT);
        else pdc.set(KEY_NEXT, PersistentDataType.LONG, playerList.getFirst().next());
        pdc.set(KEY_COOLDOWN, listPVDType, playerList);

        v.update();
    }

    private List<PlayerVaultData> refreshVault(Vault v, long now, List<PlayerVaultData> playerList) {
        var removePlayers = new ArrayList<UUID>(playerList.size());
        int idx = 0;
        for (; idx < playerList.size(); idx++) {
            PlayerVaultData pvd = playerList.get(idx);
            if (pvd.next() > now) break;
            else removePlayers.add(pvd.uuid());
        }
        if (!removePlayers.isEmpty()) {
            removePlayer(v, removePlayers);
            playerList = playerList.subList(idx, playerList.size());
        }
        return playerList;
    }

}
