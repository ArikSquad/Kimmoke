package eu.mikart.kimmoke.polar;

public final class PaletteUtil {
    private PaletteUtil() {
    }

    public static int bitsToRepresent(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be greater than 0");
        }
        return Integer.SIZE - Integer.numberOfLeadingZeros(n);
    }

    public static long[] pack(int[] values, int bitsPerEntry) {
        if (values.length == 0) {
            return new long[0];
        }

        int perLong = Math.max(1, 64 / bitsPerEntry);
        long[] packed = new long[(int) Math.ceil(values.length / (double) perLong)];
        long mask = (1L << bitsPerEntry) - 1L;

        for (int index = 0; index < values.length; index++) {
            int longIndex = index / perLong;
            int subIndex = index % perLong;
            int bitIndex = subIndex * bitsPerEntry;
            packed[longIndex] |= ((long) values[index] & mask) << bitIndex;
        }

        return packed;
    }

    public static void unpack(int[] out, long[] in, int bitsPerEntry) {
        if (out.length == 0 || in.length == 0) {
            return;
        }

        int perLong = Math.max(1, 64 / bitsPerEntry);
        long mask = (1L << bitsPerEntry) - 1L;
        for (int index = 0; index < out.length; index++) {
            int longIndex = index / perLong;
            int subIndex = index % perLong;
            int bitIndex = subIndex * bitsPerEntry;
            out[index] = (int) ((in[longIndex] >>> bitIndex) & mask);
        }
    }
}
