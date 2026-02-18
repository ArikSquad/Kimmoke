package eu.mikart.kimmoke.polar;

import com.github.luben.zstd.Zstd;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

public final class PolarReader {
    private static final int LIGHT_BYTES = 2048;

    private PolarReader() {
    }

    public static PolarWorld read(Path path) throws Exception {
        return read(Files.readAllBytes(path));
    }

    public static PolarWorld read(byte[] data) {
        return read(data, PolarDataConverter.NOOP);
    }

    public static PolarWorld read(byte[] data, PolarDataConverter converter) {
        Reader reader = new Reader(data);
        int magic = reader.readInt();
        if (magic != PolarWorld.MAGIC_NUMBER) {
            throw new IllegalStateException("Invalid polar magic number");
        }

        short version = reader.readShort();
        if (version > PolarWorld.LATEST_VERSION) {
            throw new IllegalStateException("Unsupported polar version: " + version);
        }

        int dataVersion = version >= PolarWorld.VERSION_DATA_CONVERTER
            ? reader.readVarInt()
            : converter.defaultDataVersion();

        byte compressionId = reader.readByte();
        PolarWorld.CompressionType compression = PolarWorld.CompressionType.fromId(compressionId);
        if (compression == null) {
            throw new IllegalStateException("Invalid compression type id: " + compressionId);
        }

        int uncompressedLength = reader.readVarInt();
        byte[] content = switch (compression) {
            case NONE -> reader.readRemaining();
            case ZSTD -> Zstd.decompress(reader.readRemaining(), uncompressedLength);
        };

        Reader contentReader = new Reader(content);
        byte minSection = contentReader.readByte();
        byte maxSection = contentReader.readByte();
        int sectionCount = maxSection - minSection + 1;
        if (sectionCount <= 0) {
            throw new IllegalStateException("Invalid section range");
        }

        byte[] worldUserData = version > PolarWorld.VERSION_WORLD_USERDATA
            ? contentReader.readByteArray()
            : new byte[0];

        int chunkCount = contentReader.readVarInt();
        ArrayList<PolarChunk> chunks = new ArrayList<>(Math.max(0, chunkCount));
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(readChunk(contentReader, converter, version, dataVersion, sectionCount));
        }

        return new PolarWorld(version, dataVersion, compression, minSection, maxSection, worldUserData, chunks);
    }

    private static PolarChunk readChunk(Reader reader, PolarDataConverter converter, short version, int dataVersion, int sectionCount) {
        int chunkX = reader.readVarInt();
        int chunkZ = reader.readVarInt();

        PolarSection[] sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = readSection(reader, converter, version, dataVersion);
        }

        int blockEntityCount = reader.readVarInt();
        ArrayList<PolarChunk.BlockEntity> blockEntities = new ArrayList<>(Math.max(0, blockEntityCount));
        for (int i = 0; i < blockEntityCount; i++) {
            blockEntities.add(readBlockEntity(reader, version));
        }

        int[][] heightmaps = readHeightmaps(reader, false);

        byte[] userData = version > PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT
            ? reader.readByteArray()
            : new byte[0];

        return new PolarChunk(chunkX, chunkZ, sections, blockEntities, heightmaps, userData);
    }

    private static PolarSection readSection(Reader reader, PolarDataConverter converter, short version, int dataVersion) {
        if (reader.readBoolean()) {
            return new PolarSection();
        }

        String[] blockPalette = reader.readStringList(PolarSection.BLOCK_PALETTE_SIZE);
        if (dataVersion < converter.dataVersion()) {
            converter.convertBlockPalette(blockPalette, dataVersion, converter.dataVersion());
        }

        int[] blockData = null;
        if (blockPalette.length > 1) {
            blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];
            long[] packed = reader.readLongArray();
            int bits = Math.max(1, (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2)));
            PaletteUtil.unpack(blockData, packed, bits);
        }

        String[] biomePalette = reader.readStringList(PolarSection.BIOME_PALETTE_SIZE);
        int[] biomeData = null;
        if (biomePalette.length > 1) {
            biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];
            long[] packed = reader.readLongArray();
            int bits = Math.max(1, (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2)));
            PaletteUtil.unpack(biomeData, packed, bits);
        }

        PolarSection.LightContent blockLightContent = PolarSection.LightContent.MISSING;
        byte[] blockLight = null;
        PolarSection.LightContent skyLightContent = PolarSection.LightContent.MISSING;
        byte[] skyLight = null;

        if (version > PolarWorld.VERSION_UNIFIED_LIGHT) {
            if (version >= PolarWorld.VERSION_IMPROVED_LIGHT) {
                blockLightContent = PolarSection.LightContent.VALUES[reader.readByte() & 0xff];
            } else {
                blockLightContent = reader.readBoolean() ? PolarSection.LightContent.PRESENT : PolarSection.LightContent.MISSING;
            }
            if (blockLightContent == PolarSection.LightContent.PRESENT) {
                blockLight = reader.readFixedBytes(LIGHT_BYTES);
            }

            if (version >= PolarWorld.VERSION_IMPROVED_LIGHT) {
                skyLightContent = PolarSection.LightContent.VALUES[reader.readByte() & 0xff];
            } else {
                skyLightContent = reader.readBoolean() ? PolarSection.LightContent.PRESENT : PolarSection.LightContent.MISSING;
            }
            if (skyLightContent == PolarSection.LightContent.PRESENT) {
                skyLight = reader.readFixedBytes(LIGHT_BYTES);
            }
        } else if (reader.readBoolean()) {
            blockLightContent = PolarSection.LightContent.PRESENT;
            skyLightContent = PolarSection.LightContent.PRESENT;
            blockLight = reader.readFixedBytes(LIGHT_BYTES);
            skyLight = reader.readFixedBytes(LIGHT_BYTES);
        }

        return new PolarSection(blockPalette, blockData, biomePalette, biomeData, blockLightContent, blockLight, skyLightContent, skyLight);
    }

    private static PolarChunk.BlockEntity readBlockEntity(Reader reader, short version) {
        int pos = reader.readInt();
        int x = pos & 0xF;
        int z = (pos >> 4) & 0xF;
        int y = (pos >> 8) & 0xFFFFFF;
        if ((y & 0x800000) != 0) {
            y |= ~0xFFFFFF;
        }

        String id = reader.readOptionalString();
        byte[] nbt = null;
        boolean hasNbt = version <= PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT || reader.readBoolean();
        if (hasNbt) {
            int nbtStart = reader.position();
            NbtSkipper.skipNbt(reader);
            nbt = Arrays.copyOfRange(reader.array(), nbtStart, reader.position());
        }

        return new PolarChunk.BlockEntity(x, y, z, id, nbt);
    }

    private static int[][] readHeightmaps(Reader reader, boolean skip) {
        int[][] heightmaps = skip ? null : new int[PolarChunk.MAX_HEIGHTMAPS][];
        int mask = reader.readInt();
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            if ((mask & (1 << i)) == 0) {
                continue;
            }

            long[] packed = reader.readLongArray();
            if (skip) {
                continue;
            }

            if (packed.length == 0) {
                heightmaps[i] = new int[0];
            } else {
                int bits = Math.max(1, packed.length * 64 / PolarChunk.HEIGHTMAP_SIZE);
                int[] values = new int[PolarChunk.HEIGHTMAP_SIZE];
                PaletteUtil.unpack(values, packed, bits);
                heightmaps[i] = values;
            }
        }
        return heightmaps;
    }

    static final class Reader {
        private final byte[] data;
        private int index;

        Reader(byte[] data) {
            this.data = data;
            this.index = 0;
        }

        byte[] array() {
            return data;
        }

        int position() {
            return index;
        }

        byte readByte() {
            return data[index++];
        }

        boolean readBoolean() {
            return readByte() != 0;
        }

        short readShort() {
            int b1 = readByte() & 0xFF;
            int b2 = readByte() & 0xFF;
            return (short) ((b1 << 8) | b2);
        }

        int readInt() {
            int b1 = readByte() & 0xFF;
            int b2 = readByte() & 0xFF;
            int b3 = readByte() & 0xFF;
            int b4 = readByte() & 0xFF;
            return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
        }

        long readLong() {
            return ((long) readInt() << 32) | (readInt() & 0xffffffffL);
        }

        int readVarInt() {
            int value = 0;
            int position = 0;
            while (true) {
                byte currentByte = readByte();
                value |= (currentByte & 0x7F) << position;
                if ((currentByte & 0x80) == 0) {
                    return value;
                }
                position += 7;
                if (position >= 35) {
                    throw new IllegalStateException("VarInt is too big");
                }
            }
        }

        String readString() {
            int length = readVarInt();
            if (length < 0 || index + length > data.length) {
                throw new IllegalStateException("Invalid string length");
            }
            String out = new String(data, index, length, StandardCharsets.UTF_8);
            index += length;
            return out;
        }

        String readOptionalString() {
            return readBoolean() ? readString() : null;
        }

        String[] readStringList(int maxSize) {
            int size = readVarInt();
            if (size < 0 || size > maxSize) {
                throw new IllegalStateException("Invalid list size: " + size);
            }
            String[] out = new String[size];
            for (int i = 0; i < size; i++) {
                out[i] = readString();
            }
            return out;
        }

        byte[] readByteArray() {
            int length = readVarInt();
            if (length < 0 || index + length > data.length) {
                throw new IllegalStateException("Invalid byte array length");
            }
            byte[] out = Arrays.copyOfRange(data, index, index + length);
            index += length;
            return out;
        }

        long[] readLongArray() {
            int length = readVarInt();
            long[] out = new long[length];
            for (int i = 0; i < length; i++) {
                out[i] = readLong();
            }
            return out;
        }

        byte[] readFixedBytes(int length) {
            if (index + length > data.length) {
                throw new IllegalStateException("Buffer underflow");
            }
            byte[] out = Arrays.copyOfRange(data, index, index + length);
            index += length;
            return out;
        }

        byte[] readRemaining() {
            byte[] out = Arrays.copyOfRange(data, index, data.length);
            index = data.length;
            return out;
        }

        void skip(int count) {
            index += count;
            if (index > data.length) {
                throw new IllegalStateException("Buffer underflow");
            }
        }
    }

    private static final class NbtSkipper {
        private NbtSkipper() {
        }

        static void skipNbt(Reader reader) {
            int rootType = reader.readByte() & 0xFF;
            if (rootType == 0) {
                return;
            }
            skipString(reader);
            skipPayload(reader, rootType);
        }

        private static void skipPayload(Reader reader, int type) {
            switch (type) {
                case 1 -> reader.skip(1);
                case 2 -> reader.skip(2);
                case 3 -> reader.skip(4);
                case 4 -> reader.skip(8);
                case 5 -> reader.skip(4);
                case 6 -> reader.skip(8);
                case 7 -> {
                    int size = reader.readInt();
                    reader.skip(size);
                }
                case 8 -> skipString(reader);
                case 9 -> {
                    int childType = reader.readByte() & 0xFF;
                    int size = reader.readInt();
                    for (int i = 0; i < size; i++) {
                        skipPayload(reader, childType);
                    }
                }
                case 10 -> {
                    while (true) {
                        int childType = reader.readByte() & 0xFF;
                        if (childType == 0) {
                            break;
                        }
                        skipString(reader);
                        skipPayload(reader, childType);
                    }
                }
                case 11 -> {
                    int size = reader.readInt();
                    reader.skip(size * 4);
                }
                case 12 -> {
                    int size = reader.readInt();
                    reader.skip(size * 8);
                }
                default -> throw new IllegalStateException("Unsupported NBT tag type: " + type);
            }
        }

        private static void skipString(Reader reader) {
            int len = reader.readShort() & 0xFFFF;
            reader.skip(len);
        }
    }
}
