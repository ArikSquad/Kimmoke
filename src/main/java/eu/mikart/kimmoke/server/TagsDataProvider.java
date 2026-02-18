package eu.mikart.kimmoke.server;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TagsDataProvider {
    private static final String TAGS_NBT_RESOURCE = "/tags_1_21_11.nbt";

    private TagsDataProvider() {
    }

    static List<TagRegistry> load(List<RegistryDataProvider.RegistryData> ignoredRegistries) {
        try {
            try (InputStream input = TagsDataProvider.class.getResourceAsStream(TAGS_NBT_RESOURCE)) {
                if (input == null) {
                    throw new IllegalStateException("Missing tags resource: " + TAGS_NBT_RESOURCE);
                }

                Map<String, NbtIo.Tag> root = NbtIo.readCompressedRootCompound(input);

                List<TagRegistry> result = new ArrayList<>();
                for (Map.Entry<String, NbtIo.Tag> registryEntry : root.entrySet()) {
                    String registryId = registryEntry.getKey();
                    Map<String, NbtIo.Tag> tagsCompound = asCompound(registryEntry.getValue());

                    List<TagEntry> tags = new ArrayList<>();
                    for (Map.Entry<String, NbtIo.Tag> tagEntry : tagsCompound.entrySet()) {
                        tags.add(new TagEntry(tagEntry.getKey(), extractEntryIds(tagEntry.getValue())));
                    }

                    if (!tags.isEmpty()) {
                        result.add(new TagRegistry(registryId, List.copyOf(tags)));
                    }
                }

                return List.copyOf(result);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load tags data", e);
        }
    }

    private static List<Integer> extractEntryIds(NbtIo.Tag tag) {
        if (tag.type() == 11) {
            int[] values = (int[]) tag.value();
            List<Integer> ids = new ArrayList<>(values.length);
            for (int value : values) {
                ids.add(value);
            }
            return List.copyOf(ids);
        }

        if (tag.type() == 9) {
            NbtIo.ListTag listTag = (NbtIo.ListTag) tag.value();
            if (listTag.values().isEmpty()) {
                return List.of();
            }
            if (listTag.elementType() != 3) {
                throw new IllegalStateException("Expected list<int> entries for tags");
            }
            List<Integer> ids = new ArrayList<>(listTag.values().size());
            for (Object value : listTag.values()) {
                ids.add(((Number) value).intValue());
            }
            return List.copyOf(ids);
        }

        throw new IllegalStateException("Unsupported tag entry payload type: " + tag.type());
    }

    private static Map<String, NbtIo.Tag> asCompound(NbtIo.Tag tag) {
        if (tag == null || tag.type() != 10) {
            throw new IllegalStateException("Expected compound tag");
        }
        @SuppressWarnings("unchecked")
        Map<String, NbtIo.Tag> value = (Map<String, NbtIo.Tag>) tag.value();
        return value;
    }

    record TagRegistry(String registryId, List<TagEntry> tags) {
    }

    record TagEntry(String tagId, List<Integer> entryIds) {
    }
}
