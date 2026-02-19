package eu.mikart.kimmoke.server;

import org.junit.jupiter.api.Test;

class DimRegistryTest {
    @Test
    void checkDimensionTypeOrder() {
        var registries = RegistryDataProvider.load();
        for (var registry : registries) {
            if (registry.id().equals("minecraft:dimension_type")) {
                System.out.println("Dimension type registry entries:");
                for (int i = 0; i < registry.entries().size(); i++) {
                    var entry = registry.entries().get(i);
                    System.out.println("  Index " + i + ": " + entry.key() + " (nbt=" + (entry.nbt() != null ? entry.nbt().length + " bytes" : "null") + ")");
                }
            }
        }
    }
}
