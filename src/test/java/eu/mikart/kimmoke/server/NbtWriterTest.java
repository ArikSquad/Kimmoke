package eu.mikart.kimmoke.server;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NbtWriterTest {

    @Test
    void anonymousCompoundOmitsRootName() {
        byte[] nbt = NbtWriter.anonymousCompound(Map.of("effects", "minecraft:overworld"));

        assertEquals(10, nbt[0] & 0xFF);
        assertNotEquals(0, nbt[1] & 0xFF);
    }

    @Test
    void namedCompoundIncludesRootNameLength() {
        byte[] nbt = NbtWriter.compound(Map.of("effects", "minecraft:overworld"));

        assertEquals(10, nbt[0] & 0xFF);
        assertEquals(0, nbt[1] & 0xFF);
        assertEquals(0, nbt[2] & 0xFF);
    }
}
