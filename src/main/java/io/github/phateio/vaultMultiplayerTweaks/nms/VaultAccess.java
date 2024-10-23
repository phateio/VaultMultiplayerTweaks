package io.github.phateio.vaultMultiplayerTweaks.nms;

import net.minecraft.world.level.block.entity.vault.VaultServerData;
import org.bukkit.block.Vault;
import org.bukkit.craftbukkit.block.CraftVault;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class VaultAccess {

    private static final Field rewardedPlayers, isDirty;

    static {
        var clz = VaultServerData.class;
        try {
            rewardedPlayers = clz.getDeclaredField("rewardedPlayers");
            rewardedPlayers.setAccessible(true);
            isDirty = clz.getDeclaredField("isDirty");
            isDirty.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<UUID> getRewardedPlayers(VaultServerData data) {
        try {
            return (Set<UUID>) rewardedPlayers.get(data);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void markChanged(VaultServerData data) {
        try {
            isDirty.set(data, true);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static public void removePlayer(Vault vault, UUID player) {
        if (!(vault instanceof CraftVault v)) return;

        var te = v.getTileEntity();
        var data = te.getServerData();
        var uuids = getRewardedPlayers(data);
        if (uuids.remove(player)) markChanged(data);
    }


    static public void removePlayer(Vault vault, Collection<UUID> players) {
        if (!(vault instanceof CraftVault v)) return;

        var te = v.getTileEntity();
        var data = te.getServerData();
        var uuids = getRewardedPlayers(data);
        if (uuids.removeAll(players)) markChanged(data);
    }

    static public Set<UUID> getRewardedPlayers(Vault vault) {
        if (!(vault instanceof CraftVault v)) return Collections.emptySet();

        return getRewardedPlayers(v.getTileEntity().getServerData());
    }

}
