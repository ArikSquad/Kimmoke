package eu.mikart.kimmoke.server;

import eu.mikart.kimmoke.polar.PolarWorld;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LimboWorldLoaderTest {

    @Test
    void missingFileThrows() {
        assertThrows(Exception.class, () -> LimboWorldLoader.load(Path.of("definitely-missing-limbo.polar")));
    }

    @Test
    void emptyWorldContainsSpawnChunk() {
        PolarWorld world = LimboWorldLoader.emptyWorld();

        assertNotNull(world.chunkAt(0, 0));
        assertNull(world.chunkAt(1, 1));
    }

    @Test
    void testLoad() {
        AtomicReference<PolarWorld> world = new AtomicReference<>();
        assertDoesNotThrow(() -> {
            world.set(LimboWorldLoader.load(Path.of("src/test/resources/test.polar")));
        });

        assertNotNull(world);
        assertNotNull(world.get().chunkAt(0, 0));
        assertEquals(-4, world.get().minSection());
        assertEquals(19, world.get().maxSection());
    }

}
