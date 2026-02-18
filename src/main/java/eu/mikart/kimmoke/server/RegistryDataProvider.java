package eu.mikart.kimmoke.server;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RegistryDataProvider {
    private static final String CODEC_NBT_RESOURCE = "/codec_1_21_11.nbt";

    private RegistryDataProvider() {
    }

    static List<RegistryData> load() {
        try (InputStream input = RegistryDataProvider.class.getResourceAsStream(CODEC_NBT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing codec resource: " + CODEC_NBT_RESOURCE);
            }

            Map<String, NbtIo.Tag> root = NbtIo.readCompressedRootCompound(input);

            List<RegistryData> registries = new ArrayList<>();
            for (Map.Entry<String, NbtIo.Tag> rootEntry : root.entrySet()) {
                String id = rootEntry.getKey();
                Map<String, NbtIo.Tag> registry = asCompound(rootEntry.getValue());
                NbtIo.ListTag values = asList(registry.get("value"));

                List<RegistryEntry> entries = new ArrayList<>(values.values().size());
                for (Object value : values.values()) {
                    @SuppressWarnings("unchecked")
                    Map<String, NbtIo.Tag> entry = (Map<String, NbtIo.Tag>) value;
                    String key = asString(entry.get("name"));
                    NbtIo.Tag element = entry.get("element");
                    byte[] nbt = element == null ? null : NbtIo.writeUnnamed(element);
                    entries.add(new RegistryEntry(key, nbt));
                }

                registries.add(new RegistryData(id, entries));
            }

            return List.copyOf(registries);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load registry codec", e);
        }
    }

    private static Map<String, NbtIo.Tag> asCompound(NbtIo.Tag tag) {
        if (tag == null || tag.type() != 10) {
            throw new IllegalStateException("Expected compound tag");
        }
        @SuppressWarnings("unchecked")
        Map<String, NbtIo.Tag> value = (Map<String, NbtIo.Tag>) tag.value();
        return value;
    }

    private static NbtIo.ListTag asList(NbtIo.Tag tag) {
        if (tag == null || tag.type() != 9) {
            throw new IllegalStateException("Expected list tag");
        }
        return (NbtIo.ListTag) tag.value();
    }

    private static String asString(NbtIo.Tag tag) {
        if (tag == null || tag.type() != 8) {
            throw new IllegalStateException("Expected string tag");
        }
        return (String) tag.value();
    }

    record RegistryData(String id, List<RegistryEntry> entries) {
    }

    record RegistryEntry(String key, byte[] nbt) {
    }
}
