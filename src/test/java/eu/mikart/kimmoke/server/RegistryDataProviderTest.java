package eu.mikart.kimmoke.server;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryDataProviderTest {

    @Test
    void loadsCoreRegistriesFromBundledCodec() {
        var registries = RegistryDataProvider.load();
        Set<String> ids = registries.stream().map(RegistryDataProvider.RegistryData::id).collect(Collectors.toSet());

        assertTrue(ids.contains("minecraft:dimension_type"));
        assertTrue(ids.contains("minecraft:worldgen/biome"));
        assertTrue(ids.contains("minecraft:cat_variant"));
        assertTrue(ids.contains("minecraft:wolf_variant"));

        var cat = registries.stream().filter(r -> r.id().equals("minecraft:cat_variant")).findFirst().orElseThrow();
        assertTrue(!cat.entries().isEmpty());
    }
}
