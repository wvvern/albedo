package dev.albedo.core.texture;

import java.util.function.Supplier;

/** A cached value that a read with a new stamp builds again. */
final class Stamped<T> {
    private static final class Built<T> {
        final int stamp;
        final T value;

        Built(int stamp, T value) {
            this.stamp = stamp;
            this.value = value;
        }
    }

    /** One reference, so that a reader never sees the stamp of one build with the value of another. */
    private volatile Built<T> built;

    T get(int stamp, Supplier<T> build) {
        Built<T> b = built;
        if (b == null || b.stamp != stamp) {
            b = new Built<T>(stamp, build.get());
            built = b;
        }
        return b.value;
    }
}
