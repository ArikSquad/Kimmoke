package eu.mikart.kimmoke.server;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagsDataProviderTest {

    @Test
    void loadsCriticalTagRegistriesAndTags() {
        var registryData = RegistryDataProvider.load();
        var tags = TagsDataProvider.load(registryData);

        Set<String> ids = tags.stream().map(TagsDataProvider.TagRegistry::registryId).collect(Collectors.toSet());
        assertTrue(ids.contains("minecraft:item"));
        assertTrue(ids.contains("minecraft:block"));
        assertTrue(ids.contains("minecraft:entity_type"));
        assertTrue(ids.contains("minecraft:enchantment"));
        assertTrue(ids.contains("minecraft:dialog"));
        assertTrue(ids.contains("minecraft:timeline"));

        var items = tags.stream().filter(t -> t.registryId().equals("minecraft:item")).findFirst().orElseThrow();
        var enchantableArmor = items.tags().stream()
            .filter(t -> t.tagId().equals("minecraft:enchantable/armor"))
            .findFirst()
            .orElseThrow();
        assertFalse(enchantableArmor.entryIds().isEmpty());

        var blocks = tags.stream().filter(t -> t.registryId().equals("minecraft:block")).findFirst().orElseThrow();
        var soulSpeedBlocks = blocks.tags().stream()
            .filter(t -> t.tagId().equals("minecraft:soul_speed_blocks"))
            .findFirst()
            .orElseThrow();
        assertFalse(soulSpeedBlocks.entryIds().isEmpty());

        var entities = tags.stream().filter(t -> t.registryId().equals("minecraft:entity_type")).findFirst().orElseThrow();
        var sensitiveToImpaling = entities.tags().stream()
            .filter(t -> t.tagId().equals("minecraft:sensitive_to_impaling"))
            .findFirst()
            .orElseThrow();
        assertFalse(sensitiveToImpaling.entryIds().isEmpty());

        var dialog = tags.stream().filter(t -> t.registryId().equals("minecraft:dialog")).findFirst().orElseThrow();
        assertTrue(dialog.tags().stream().anyMatch(t -> t.tagId().equals("minecraft:pause_screen_additions")));
        assertTrue(dialog.tags().stream().anyMatch(t -> t.tagId().equals("minecraft:quick_actions")));
    }
}
