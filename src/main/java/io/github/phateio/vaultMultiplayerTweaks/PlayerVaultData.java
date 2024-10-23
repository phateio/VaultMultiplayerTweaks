package io.github.phateio.vaultMultiplayerTweaks;

import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.UUID;

public record PlayerVaultData(UUID uuid, long next) {

    public static final PDType TYPE = new PDType();

    public static class PDType implements PersistentDataType<byte[], PlayerVaultData> {
        @Override
        @NotNull
        public Class<byte[]> getPrimitiveType() {
            return byte[].class;
        }

        @Override
        @NotNull
        public Class<PlayerVaultData> getComplexType() {
            return PlayerVaultData.class;
        }

        @Override
        public byte @NotNull [] toPrimitive(PlayerVaultData data, @NotNull PersistentDataAdapterContext context) {
            byte[] bs = new byte[24];
            ByteBuffer.wrap(bs)
                    .putLong(data.uuid.getMostSignificantBits())
                    .putLong(data.uuid.getLeastSignificantBits())
                    .putLong(data.next);
            return bs;
        }

        @Override
        @NotNull
        public PlayerVaultData fromPrimitive(byte @NotNull [] primitive, @NotNull PersistentDataAdapterContext context) {
            ByteBuffer bb = ByteBuffer.wrap(primitive);
            long firstLong = bb.getLong();
            long secondLong = bb.getLong();
            long next = bb.getLong();
            return new PlayerVaultData(new UUID(firstLong, secondLong), next);
        }
    }
}
