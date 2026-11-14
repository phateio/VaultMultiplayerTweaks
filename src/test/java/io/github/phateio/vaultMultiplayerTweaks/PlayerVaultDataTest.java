package io.github.phateio.vaultMultiplayerTweaks;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** PDC byte layout must stay readable by data written with v1 (uuid msb, uuid lsb, next; big-endian). */
class PlayerVaultDataTest {
    final PlayerVaultData data = new PlayerVaultData(UUID.fromString("01234567-89ab-cdef-0011-223344556677"), 0x0102030405060708L);
    final String v1Bytes = "0123456789abcdef" + "0011223344556677" + "0102030405060708";

    @Test
    void encodesInV1Layout() {
        assertEquals(v1Bytes, HexFormat.of().formatHex(PlayerVaultData.TYPE.toPrimitive(data, null)));
    }

    @Test
    void decodesV1Bytes() {
        assertEquals(data, PlayerVaultData.TYPE.fromPrimitive(HexFormat.of().parseHex(v1Bytes), null));
    }
}
