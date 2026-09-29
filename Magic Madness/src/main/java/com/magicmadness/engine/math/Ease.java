package com.magicmadness.engine.math;

// ============================================================================
// MAGIC MADNESS — MATHEMATICAL EASING CURVES
// ============================================================================
public final class Ease {

    // #region 1. EASING FUNCTIONS
    private Ease() {}

    public static float clamp01(float t) {
        return t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
    }

    public static float smooth(float t) {
        float c = clamp01(t);
        return c * c * (3.0F - 2.0F * c);
    }

    public static float backOut(float t) {
        float c = clamp01(t);
        float s = 1.70158F;
        float u = c - 1.0F;
        return 1.0F + (s + 1.0F) * u * u * u + s * u * u;
    }

    public static double outBack(double t) {
        return backOut((float) t);
    }

    public static double outCubic(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        double u = 1.0 - c;
        return 1.0 - u * u * u;
    }

    public static double outQuad(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return 1.0 - (1.0 - c) * (1.0 - c);
    }

    public static double outExpo(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return c >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * c);
    }

    public static float bump(float t) {
        float c = clamp01(t);
        return (float) Math.sin(c * Math.PI);
    }
    // #endregion
}
