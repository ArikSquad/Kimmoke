package eu.mikart.kimmoke.server;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

final class NbtIo {
    private NbtIo() {
    }

    static Map<String, Tag> readCompressedRootCompound(InputStream input) throws IOException {
        try (DataInputStream data = new DataInputStream(new GZIPInputStream(input))) {
            int rootType = data.readUnsignedByte();
            if (rootType != 10) {
                throw new IOException("Expected root compound, got type " + rootType);
            }
            readString(data);
            return readCompoundPayload(data);
        }
    }

    static byte[] writeUnnamed(Tag tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag.type() & 0xFF);
        writePayload(out, tag);
        return out.toByteArray();
    }

    private static Map<String, Tag> readCompoundPayload(DataInputStream in) throws IOException {
        Map<String, Tag> values = new LinkedHashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) {
                return values;
            }
            String name = readString(in);
            Object payload = readPayload(in, type);
            values.put(name, new Tag((byte) type, payload));
        }
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                int length = in.readInt();
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                yield bytes;
            }
            case 8 -> readString(in);
            case 9 -> {
                int elementType = in.readUnsignedByte();
                int length = in.readInt();
                List<Object> values = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    values.add(readPayload(in, elementType));
                }
                yield new ListTag((byte) elementType, values);
            }
            case 10 -> readCompoundPayload(in);
            case 11 -> {
                int length = in.readInt();
                int[] values = new int[length];
                for (int i = 0; i < length; i++) {
                    values[i] = in.readInt();
                }
                yield values;
            }
            case 12 -> {
                int length = in.readInt();
                long[] values = new long[length];
                for (int i = 0; i < length; i++) {
                    values[i] = in.readLong();
                }
                yield values;
            }
            default -> throw new IOException("Unsupported NBT type: " + type);
        };
    }

    private static void writePayload(ByteArrayOutputStream out, Tag tag) {
        switch (tag.type()) {
            case 0 -> {
            }
            case 1 -> out.write(((Number) tag.value()).byteValue());
            case 2 -> writeShort(out, ((Number) tag.value()).shortValue());
            case 3 -> writeInt(out, ((Number) tag.value()).intValue());
            case 4 -> writeLong(out, ((Number) tag.value()).longValue());
            case 5 -> writeInt(out, Float.floatToIntBits(((Number) tag.value()).floatValue()));
            case 6 -> writeLong(out, Double.doubleToLongBits(((Number) tag.value()).doubleValue()));
            case 7 -> {
                byte[] bytes = (byte[]) tag.value();
                writeInt(out, bytes.length);
                out.writeBytes(bytes);
            }
            case 8 -> writeString(out, (String) tag.value());
            case 9 -> {
                ListTag listTag = (ListTag) tag.value();
                out.write(listTag.elementType() & 0xFF);
                writeInt(out, listTag.values().size());
                for (Object value : listTag.values()) {
                    writePayload(out, new Tag(listTag.elementType(), value));
                }
            }
            case 10 -> {
                @SuppressWarnings("unchecked")
                Map<String, Tag> values = (Map<String, Tag>) tag.value();
                for (Map.Entry<String, Tag> entry : values.entrySet()) {
                    Tag child = entry.getValue();
                    out.write(child.type() & 0xFF);
                    writeString(out, entry.getKey());
                    writePayload(out, child);
                }
                out.write(0);
            }
            case 11 -> {
                int[] values = (int[]) tag.value();
                writeInt(out, values.length);
                for (int value : values) {
                    writeInt(out, value);
                }
            }
            case 12 -> {
                long[] values = (long[]) tag.value();
                writeInt(out, values.length);
                for (long value : values) {
                    writeLong(out, value);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported NBT type: " + tag.type());
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int length;
        try {
            length = in.readUnsignedShort();
        } catch (EOFException e) {
            throw new IOException("Unexpected EOF while reading NBT string", e);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeShort(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        writeInt(out, (int) (value >>> 32));
        writeInt(out, (int) value);
    }

    record Tag(byte type, Object value) {
    }

    record ListTag(byte elementType, List<Object> values) {
    }
}