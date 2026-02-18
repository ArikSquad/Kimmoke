package eu.mikart.kimmoke.server;

import eu.mikart.kimmoke.polar.PolarChunk;
import eu.mikart.kimmoke.polar.PolarReader;
import eu.mikart.kimmoke.polar.PolarSection;
import eu.mikart.kimmoke.polar.PolarWorld;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LimboWorldLoader {
    private LimboWorldLoader() {
    }

    public static PolarWorld load(Path path) {
        try {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return PolarReader.read(path);
            }
        } catch (Exception ignored) {
        }
        return emptyWorld();
    }

    public static PolarWorld emptyWorld() {
        PolarWorld world = new PolarWorld();
        byte minSection = world.minSection();
        byte maxSection = world.maxSection();

        PolarSection[] sections = new PolarSection[maxSection - minSection + 1];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new PolarSection();
        }

        int[][] heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            heightmaps[i] = null;
        }

        PolarChunk chunk = new PolarChunk(0, 0, sections, List.of(), heightmaps, new byte[0]);
        world.updateChunkAt(0, 0, chunk);
        return world;
    }

    public static List<PolarChunk> chunksAroundSpawn(PolarWorld world, int radius) {
        List<PolarChunk> chunks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                PolarChunk chunk = world.chunkAt(x, z);
                if (chunk != null) {
                    chunks.add(chunk);
                }
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(emptyWorld().chunkAt(0, 0));
        }
        return chunks;
    }
}
