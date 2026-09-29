package com.magicmadness.engine.math;

// #region 1. IMPORTS
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — DETERMINISTIC 3D NOISE & DIRECTION GENERATOR
// ============================================================================
public final class Noise {

    // #region 2. HASH, 1D/3D INTERPOLATED NOISE & FBM
    private Noise() {}

    public static float hash(int seed, int step, int salt) {
        int h = seed * 374761393 + step * 668265263 + salt * 1442695041;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return ((h & 0x7FFFFFFF) / (float) 0x3FFFFFFF) - 1.0F;
    }

    public static float of(int seed, float step, int salt) {
        int i = (int) Math.floor(step);
        float f = step - i;
        float u = f * f * (3.0F - 2.0F * f);
        float a = hash(seed, i, salt);
        float b = hash(seed, i + 1, salt);
        return a + (b - a) * u;
    }

    public static double signed(double x, double y, double z) {
        int ix = (int) Math.floor(x * 13.0 + y * 7.0);
        int iz = (int) Math.floor(z * 19.0 + y * 3.0);
        return of(ix, (float) (x + y * 0.5), iz);
    }

    public static double fbm(double x, double y, double z, int octaves) {
        double sum = 0.0;
        double amp = 0.5;
        double freq = 1.0;
        for (int i = 0; i < octaves; i++) {
            sum += signed(x * freq, y * freq, z * freq) * amp;
            freq *= 2.02;
            amp *= 0.5;
        }
        return sum;
    }

    public static Vec3 direction(int seed, float step) {
        double x = of(seed, step, 11);
        double y = of(seed, step, 37);
        double z = of(seed, step, 73);
        Vec3 v = new Vec3(x, y, z);
        double len = v.length();
        return len < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : v.scale(1.0 / len);
    }
    // #endregion
}
