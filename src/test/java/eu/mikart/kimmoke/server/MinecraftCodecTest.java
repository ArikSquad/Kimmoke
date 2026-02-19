package eu.mikart.kimmoke.server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftCodecTest {

    @Test
    void varIntRoundTrip() {
        int[] values = {0, 1, 2, 127, 128, 255, 32767, 2097151};

        for (int value : values) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MinecraftCodec.writeVarInt(out, value);

            ByteBuffer buffer = ByteBuffer.wrap(out.toByteArray());
            int decoded = MinecraftCodec.readVarInt(buffer);
            assertEquals(value, decoded);
        }
    }

    @Test
    void framedPacketPrependsLengthAndId() {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftCodec.writeVarInt(payload, 42);

        ByteBuffer framed = MinecraftCodec.framedPacket(0x30, payload);

        int totalLength = MinecraftCodec.readVarInt(framed);
        assertEquals(framed.remaining(), totalLength);

        int packetId = MinecraftCodec.readVarInt(framed);
        assertEquals(0x30, packetId);

        int body = MinecraftCodec.readVarInt(framed);
        assertEquals(42, body);
    }
}
