package eu.mikart.kimmoke.server;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class NbtWriter {
    private NbtWriter() {
    }

    static byte[] anonymousCompound(Map<String, Object> values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(10);
        writeCompoundPayload(out, values);
        return out.toByteArray();
    }

    static byte[] compound(Map<String, Object> values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(10);
        MinecraftCodec.writeShort(out, 0);
        writeCompoundPayload(out, values);
        return out.toByteArray();
    }

    static byte[] anonymousFromTypedNode(Object typedNode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTypedNodeRoot(out, typedNode);
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static void writeTypedNodeRoot(ByteArrayOutputStream out, Object typedNode) {
        Map<String, Object> node = (Map<String, Object>) typedNode;
        String type = (String) node.get("type");
        Object value = node.get("value");

        out.write(tagId(type));
        writeTypedPayload(out, type, value);
    }

    @SuppressWarnings("unchecked")
    private static void writeTypedPayload(ByteArrayOutputStream out, String type, Object value) {
        switch (type) {
            case "end" -> {
            }
            case "byte" -> out.write(((Number) value).byteValue());
            case "short" -> MinecraftCodec.writeShort(out, ((Number) value).shortValue());
            case "int" -> MinecraftCodec.writeInt(out, ((Number) value).intValue());
            case "long" -> MinecraftCodec.writeLong(out, ((Number) value).longValue());
            case "float" -> MinecraftCodec.writeFloat(out, ((Number) value).floatValue());
            case "double" -> MinecraftCodec.writeDouble(out, ((Number) value).doubleValue());
            case "byteArray" -> {
                List<Object> list = (List<Object>) value;
                MinecraftCodec.writeInt(out, list.size());
                for (Object entry : list) {
                    out.write(((Number) entry).byteValue());
                }
            }
            case "string" -> writeString(out, String.valueOf(value));
            case "list" -> {
                Map<String, Object> listNode = (Map<String, Object>) value;
                String elemType = (String) listNode.get("type");
                List<Object> elems = (List<Object>) listNode.get("value");

                out.write(tagId(elemType));
                MinecraftCodec.writeInt(out, elems.size());
                for (Object elem : elems) {
                    Object elemValue = elem;
                    if (!("compound".equals(elemType) || "list".equals(elemType))) {
                        if (elem instanceof Map<?, ?> map
                            && map.get("type") instanceof String
                            && map.containsKey("value")) {
                            elemValue = map.get("value");
                        }
                    }
                    writeTypedPayload(out, elemType, normalizeTypedValue(elemType, elemValue));
                }
            }
            case "compound" -> {
                Map<String, Object> compound = (Map<String, Object>) value;
                for (Map.Entry<String, Object> entry : compound.entrySet()) {
                    Map<String, Object> child = (Map<String, Object>) entry.getValue();
                    String childType = (String) child.get("type");
                    Object childValue = normalizeTypedValue(childType, child.get("value"));

                    out.write(tagId(childType));
                    writeName(out, entry.getKey());
                    writeTypedPayload(out, childType, childValue);
                }
                out.write(0);
            }
            case "intArray" -> {
                List<Object> list = (List<Object>) value;
                MinecraftCodec.writeInt(out, list.size());
                for (Object entry : list) {
                    MinecraftCodec.writeInt(out, ((Number) entry).intValue());
                }
            }
            case "longArray" -> {
                List<Object> list = (List<Object>) value;
                MinecraftCodec.writeInt(out, list.size());
                for (Object entry : list) {
                    MinecraftCodec.writeLong(out, ((Number) entry).longValue());
                }
            }
            default -> throw new IllegalArgumentException("Unsupported typed NBT type: " + type);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeTypedValue(String type, Object value) {
        if ("compound".equals(type)) {
            if (value instanceof Map<?, ?> map
                && map.get("type") instanceof String
                && map.containsKey("value")
                && map.size() == 2) {
                return ((Map<String, Object>) value).get("value");
            }
            return value;
        }
        if ("list".equals(type)) {
            if (value instanceof Map<?, ?> map
                && map.get("type") instanceof String
                && map.containsKey("value")) {
                return value;
            }
            throw new IllegalArgumentException("Invalid list payload");
        }
        return value;
    }

    private static int tagId(String type) {
        return switch (type) {
            case "end" -> 0;
            case "byte" -> 1;
            case "short" -> 2;
            case "int" -> 3;
            case "long" -> 4;
            case "float" -> 5;
            case "double" -> 6;
            case "byteArray" -> 7;
            case "string" -> 8;
            case "list" -> 9;
            case "compound" -> 10;
            case "intArray" -> 11;
            case "longArray" -> 12;
            default -> throw new IllegalArgumentException("Unknown NBT type: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private static void writeTag(ByteArrayOutputStream out, String key, Object value) {
        if (value instanceof Byte b) {
            out.write(1);
            writeName(out, key);
            out.write(b);
        } else if (value instanceof Integer i) {
            out.write(3);
            writeName(out, key);
            MinecraftCodec.writeInt(out, i);
        } else if (value instanceof Long l) {
            out.write(4);
            writeName(out, key);
            MinecraftCodec.writeLong(out, l);
        } else if (value instanceof Float f) {
            out.write(5);
            writeName(out, key);
            MinecraftCodec.writeFloat(out, f);
        } else if (value instanceof Double d) {
            out.write(6);
            writeName(out, key);
            MinecraftCodec.writeDouble(out, d);
        } else if (value instanceof String s) {
            out.write(8);
            writeName(out, key);
            writeString(out, s);
        } else if (value instanceof Map<?, ?> m) {
            out.write(10);
            writeName(out, key);
            writeCompoundPayload(out, (Map<String, Object>) m);
        } else if (value instanceof List<?> list) {
            out.write(9);
            writeName(out, key);
            writeList(out, (List<Object>) list);
        } else {
            throw new IllegalArgumentException("Unsupported NBT type: " + value.getClass());
        }
    }

    private static void writeList(ByteArrayOutputStream out, List<Object> list) {
        if (list.isEmpty()) {
            out.write(1);
            MinecraftCodec.writeInt(out, 0);
            return;
        }

        Object first = list.get(0);
        if (first instanceof String) {
            out.write(8);
            MinecraftCodec.writeInt(out, list.size());
            for (Object value : list) {
                writeString(out, (String) value);
            }
            return;
        }

        if (first instanceof Map<?, ?>) {
            out.write(10);
            MinecraftCodec.writeInt(out, list.size());
            for (Object value : list) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                writeCompoundPayload(out, map);
            }
            return;
        }

        throw new IllegalArgumentException("Unsupported NBT list type: " + first.getClass());
    }

    private static void writeCompoundPayload(ByteArrayOutputStream out, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            writeTag(out, entry.getKey(), entry.getValue());
        }
        out.write(0);
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        MinecraftCodec.writeShort(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeName(ByteArrayOutputStream out, String key) {
        writeString(out, key);
    }
}
