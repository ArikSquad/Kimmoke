package eu.mikart.kimmoke.polar;

import com.github.luben.zstd.Zstd;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class PolarWriter {
    private PolarWriter() {
    }

    public static byte[] write(PolarWorld world) {
        ByteArrayOutputStream content = new ByteArrayOutputStream(4096);
        writeByte(content, world.minSection());
        writeByte(content, world.maxSection());
        writeByteArray(content, world.userData());

        writeVarInt(content, world.chunks().size());
        for (PolarChunk chunk : world.chunks()) {
            writeChunk(content, chunk, world.sectionCount());
        }

        byte[] contentBytes = content.toByteArray();
        byte[] payload = world.compression() == PolarWorld.CompressionType.ZSTD
            ? Zstd.compress(contentBytes)
            : contentBytes;

        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 64);
        writeInt(out, PolarWorld.MAGIC_NUMBER);
        writeShort(out, PolarWorld.VERSION_IMPROVED_LIGHT);
        writeVarInt(out, world.dataVersion());
        writeByte(out, world.compression().ordinal());
        writeVarInt(out, contentBytes.length);
        out.writeBytes(payload);

        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, PolarChunk chunk, int sectionCount) {
        writeVarInt(out, chunk.x());
        writeVarInt(out, chunk.z());

        if (chunk.sections().length != sectionCount) {
            throw new IllegalStateException("Chunk section count mismatch");
        }

        for (PolarSection section : chunk.sections()) {
            writeSection(out, section);
        }

        writeVarInt(out, chunk.blockEntities().size());
        for (PolarChunk.BlockEntity blockEntity : chunk.blockEntities()) {
            writeBlockEntity(out, blockEntity);
        }

        int mask = 0;
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            if (chunk.heightmap(i) != null) {
                mask |= 1 << i;
            }
        }
        writeInt(out, mask);

        int bitsPerEntry = Math.max(1, PaletteUtil.bitsToRepresent(sectionCount * 16));
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            int[] heightmap = chunk.heightmap(i);
            if (heightmap == null) {
                continue;
            }
            long[] packed = heightmap.length == 0 ? new long[0] : PaletteUtil.pack(heightmap, bitsPerEntry);
            writeLongArray(out, packed);
        }

        writeByteArray(out, chunk.userData());
    }

    private static void writeSection(ByteArrayOutputStream out, PolarSection section) {
        writeBoolean(out, section.isEmpty());
        if (section.isEmpty()) {
            return;
        }

        writeStringList(out, section.blockPalette());
        if (section.blockPalette().length > 1) {
            int bits = Math.max(1, (int) Math.ceil(Math.log(section.blockPalette().length) / Math.log(2)));
            writeLongArray(out, PaletteUtil.pack(section.blockData(), bits));
        }

        writeStringList(out, section.biomePalette());
        if (section.biomePalette().length > 1) {
            int bits = Math.max(1, (int) Math.ceil(Math.log(section.biomePalette().length) / Math.log(2)));
            writeLongArray(out, PaletteUtil.pack(section.biomeData(), bits));
        }

        writeByte(out, section.blockLightContent().ordinal());
        if (section.blockLightContent() == PolarSection.LightContent.PRESENT) {
            out.writeBytes(section.blockLight());
        }

        writeByte(out, section.skyLightContent().ordinal());
        if (section.skyLightContent() == PolarSection.LightContent.PRESENT) {
            out.writeBytes(section.skyLight());
        }
    }

    private static void writeBlockEntity(ByteArrayOutputStream out, PolarChunk.BlockEntity blockEntity) {
        int y = blockEntity.y() & 0xFFFFFF;
        int index = (blockEntity.x() & 0xF) | ((blockEntity.z() & 0xF) << 4) | (y << 8);
        writeInt(out, index);

        writeBoolean(out, blockEntity.id() != null);
        if (blockEntity.id() != null) {
            writeString(out, blockEntity.id());
        }

        writeBoolean(out, blockEntity.nbtData() != null);
        if (blockEntity.nbtData() != null) {
            out.writeBytes(blockEntity.nbtData());
        }
    }

    private static void writeStringList(ByteArrayOutputStream out, String[] values) {
        writeVarInt(out, values.length);
        for (String value : values) {
            writeString(out, value);
        }
    }

    private static void writeByteArray(ByteArrayOutputStream out, byte[] bytes) {
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeLongArray(ByteArrayOutputStream out, long[] values) {
        writeVarInt(out, values.length);
        for (long value : values) {
            writeLong(out, value);
        }
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeBoolean(ByteArrayOutputStream out, boolean value) {
        writeByte(out, value ? 1 : 0);
    }

    private static void writeByte(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
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

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }
}
