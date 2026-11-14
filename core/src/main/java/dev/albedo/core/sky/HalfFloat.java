package dev.albedo.core.sky;

/** IEEE half float bits of the term images. */
final class HalfFloat {
    private HalfFloat() {
    }

    /** The half float bits of a value that can be negative, limited to the range from -65504 to 65504. */
    static short encodeSigned(float v) {
        return v < 0 ? (short) (encode(-v) | 0x8000) : encode(v);
    }

    /** The half float bits of a value, which is limited to the range from 0 to 65504 and rounded to the nearest half float. */
    static short encode(float v) {
        // This test is also false for NaN, which then gives 0 as a negative value does.
        if (!(v > 0)) return 0;
        int bits = Float.floatToIntBits(Math.min(65504f, v));
        int exp = (bits >>> 23) - 127 + 15;
        int mantissa = bits & 0x7FFFFF;
        if (exp > 0) return (short) (((exp << 10) | (mantissa >> 13)) + ((mantissa >> 12) & 1));
        // A value below the smallest normal half float is a subnormal: its mantissa with the hidden bit, moved right.
        if (exp < -10) return 0;
        int shift = 14 - exp;
        return (short) (((mantissa | 0x800000) + (1 << (shift - 1))) >> shift);
    }

    /** The value of half float bits. */
    static float decode(short bits) {
        int exp = (bits >> 10) & 0x1F, mantissa = bits & 0x3FF;
        float v = exp == 0 ? mantissa / 16777216f : (float) ((1 + mantissa / 1024.0) * Math.pow(2, exp - 15));
        return (bits & 0x8000) != 0 ? -v : v;
    }
}
