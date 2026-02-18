package eu.mikart.kimmoke.server;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class MinecraftCodec {
    private MinecraftCodec() {
    }

    static int readVarInt(ByteBuffer buffer) {
        int value = 0;
        int position = 0;
        while (buffer.hasRemaining()) {
            byte current = buffer.get();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 35) {
                throw new IllegalStateException("VarInt too big");
            }
        }
        throw new IllegalStateException("Incomplete VarInt");
    }

    static int tryReadVarInt(ByteBuffer buffer) {
        int start = buffer.position();
        try {
            return readVarInt(buffer);
        } catch (IllegalStateException e) {
            buffer.position(start);
            return Integer.MIN_VALUE;
        }
    }

    static String readString(ByteBuffer buffer) {
        int length = readVarInt(buffer);
        if (length < 0 || length > buffer.remaining()) {
            throw new IllegalStateException("Invalid string length");
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static UUID readUuid(ByteBuffer buffer) {
        long msb = buffer.getLong();
        long lsb = buffer.getLong();
        return new UUID(msb, lsb);
    }

    static void writeVarInt(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    static void writeBoolean(ByteArrayOutputStream out, boolean value) {
        out.write(value ? 1 : 0);
    }

    static void writeByte(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
    }

    static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    static void writeLong(ByteArrayOutputStream out, long value) {
        writeInt(out, (int) (value >>> 32));
        writeInt(out, (int) value);
    }

    static void writeFloat(ByteArrayOutputStream out, float value) {
        writeInt(out, Float.floatToIntBits(value));
    }

    static void writeDouble(ByteArrayOutputStream out, double value) {
        writeLong(out, Double.doubleToLongBits(value));
    }

    static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    static void writeUuid(ByteArrayOutputStream out, UUID uuid) {
        writeLong(out, uuid.getMostSignificantBits());
        writeLong(out, uuid.getLeastSignificantBits());
    }

    static void writePosition(ByteArrayOutputStream out, int x, int y, int z) {
        long packed = ((long) (x & 0x3FFFFFF) << 38)
            | ((long) (z & 0x3FFFFFF) << 12)
            | (y & 0xFFFL);
        writeLong(out, packed);
    }

    static ByteBuffer framedPacket(int packetId, ByteArrayOutputStream payload) {
        ByteArrayOutputStream full = new ByteArrayOutputStream(payload.size() + 10);
        writeVarInt(full, packetId);
        full.writeBytes(payload.toByteArray());

        ByteArrayOutputStream framed = new ByteArrayOutputStream(full.size() + 5);
        writeVarInt(framed, full.size());
        framed.writeBytes(full.toByteArray());
        return ByteBuffer.wrap(framed.toByteArray());
    }
}
