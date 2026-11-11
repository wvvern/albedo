package dev.albedo.modern;

//? if <1.19.3 {
/*import com.mojang.math.Matrix4f;

import java.nio.FloatBuffer;

// The helpers for the versions whose matrices are com.mojang.math types.
public final class MojangMath {
    private MojangMath() {}

    /^* The 16 floats of a matrix in column order, which is how a GL uniform takes them. ^/
    public static float[] columns(Matrix4f matrix) {
        FloatBuffer buffer = FloatBuffer.allocate(16);
        matrix.store(buffer);
        return buffer.array();
    }
}
*///?}
