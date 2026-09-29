package com.magicmadness.engine.math;

// #region 1. IMPORTS
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — ORTHONORMAL 3D BASIS & VECTOR MATH
// ============================================================================
public final class Vectors {

    // #region 2. CONSTANTS, BASIS RECORD & VECTOR HELPERS
    public static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private Vectors() {}

    public record Basis(Vec3 n, Vec3 u, Vec3 v) {}

    public static Basis basis(Vec3 direction) {
        Vec3 n = direction.lengthSqr() < 1.0E-6 ? UP : direction.normalize();
        Vec3 ref = Math.abs(n.y) > 0.92 ? new Vec3(1.0, 0.0, 0.0) : UP;
        Vec3 u = n.cross(ref).normalize();
        Vec3 v = n.cross(u).normalize();
        return new Basis(n, u, v);
    }

    public static Vec3 perpendicular(Vec3 direction) {
        return basis(direction).u();
    }

    public static Vec3 circleOffset(Vec3 axis, double radius, double angleRad) {
        Basis b = basis(axis);
        double cos = Math.cos(angleRad) * radius;
        double sin = Math.sin(angleRad) * radius;
        return b.u().scale(cos).add(b.v().scale(sin));
    }
    // #endregion
}
