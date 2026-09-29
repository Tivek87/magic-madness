package com.magicmadness.engine.fx;

// #region 1. IMPORTS
import com.magicmadness.engine.math.Vectors;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
// #endregion

// ============================================================================
// MAGIC MADNESS — SERVER-SIDE GEOMETRIC PARTICLE ENGINE (FIBONACCI, STARS, HELICES)
// ============================================================================
public final class ParticleFx {

    // #region 2. CONSTANTS & DUST OPTIONS
    public static final double GOLDEN_ANGLE = 2.399963229728653;
    private static final double VIEW_RANGE_SQR = 128.0 * 128.0;

    private ParticleFx() {}

    public static Vector3f rgbVec(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        return new Vector3f(r, g, b);
    }

    public static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(rgbVec(rgb), Math.max(0.01F, Math.min(4.0F, scale)));
    }

    public static DustColorTransitionOptions fade(int fromRgb, int toRgb, float scale) {
        return new DustColorTransitionOptions(rgbVec(fromRgb), rgbVec(toRgb), Math.max(0.01F, Math.min(4.0F, scale)));
    }

    public static void send(ServerLevel level, ParticleOptions particle, Vec3 pos, int count,
                            double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.x, pos.y, pos.z) <= VIEW_RANGE_SQR) {
                level.sendParticles(player, particle, true, pos.x, pos.y, pos.z, count, dx, dy, dz, speed);
            }
        }
    }

    public static void burst(ServerLevel level, ParticleOptions particle, Vec3 center,
                             int count, double spread, double speed) {
        send(level, particle, center, count, spread, spread, spread, speed);
    }
    // #endregion

    // #region 3. MATHEMATICAL 3D SHAPES
    public static void disc(ServerLevel level, ParticleOptions particle, Vec3 center, Vec3 normal,
                            double radius, int points, double speed) {
        Vectors.Basis b = Vectors.basis(normal);
        for (int i = 0; i < points; i++) {
            double a = (2.0 * Math.PI * i) / points;
            double r = radius * Math.sqrt((i + 0.5) / points);
            Vec3 p = center.add(b.u().scale(Math.cos(a) * r)).add(b.v().scale(Math.sin(a) * r));
            send(level, particle, p, 1, 0.0, 0.0, 0.0, speed);
        }
    }

    public static void star(ServerLevel level, ParticleOptions particle, Vec3 center, Vec3 normal,
                            int tips, double radius, int perEdge, double speed) {
        Vectors.Basis b = Vectors.basis(normal);
        int skip = tips >= 5 ? 2 : 1;
        Vec3[] verts = new Vec3[tips];
        for (int i = 0; i < tips; i++) {
            double a = (2.0 * Math.PI * i) / tips;
            verts[i] = center.add(b.u().scale(Math.cos(a) * radius)).add(b.v().scale(Math.sin(a) * radius));
        }
        for (int i = 0; i < tips; i++) {
            Vec3 from = verts[i];
            Vec3 to = verts[(i + skip) % tips];
            for (int s = 0; s < perEdge; s++) {
                double t = s / (double) perEdge;
                send(level, particle, from.lerp(to, t), 1, 0.0, 0.0, 0.0, speed);
            }
        }
    }

    public static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, Vec3 normal,
                            double radius, int points, double speed) {
        Vectors.Basis b = Vectors.basis(normal);
        for (int i = 0; i < points; i++) {
            double a = (2.0 * Math.PI * i) / points;
            Vec3 p = center.add(b.u().scale(Math.cos(a) * radius)).add(b.v().scale(Math.sin(a) * radius));
            send(level, particle, p, 1, 0.0, 0.0, 0.0, speed);
        }
    }

    public static void sphere(ServerLevel level, ParticleOptions particle, Vec3 center,
                              double radius, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double y = 1.0 - (2.0 * i + 1.0) / count;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = GOLDEN_ANGLE * i;
            double dx = Math.cos(theta) * r;
            double dz = Math.sin(theta) * r;
            Vec3 p = center.add(dx * radius, y * radius, dz * radius);
            send(level, particle, p, 1, 0.0, 0.0, 0.0, speed);
        }
    }

    public static void helix(ServerLevel level, ParticleOptions particle, Vec3 start, Vec3 direction,
                             double length, double radius, double turns, int strands, int pointsPerStrand,
                             double phaseOffset, double speed) {
        Vectors.Basis b = Vectors.basis(direction);
        for (int st = 0; st < strands; st++) {
            double strandPhase = phaseOffset + (Math.PI * 2.0 * st) / strands;
            for (int i = 0; i < pointsPerStrand; i++) {
                double f = i / (double) Math.max(1, pointsPerStrand);
                double angle = strandPhase + f * turns * Math.PI * 2.0;
                Vec3 spine = start.add(b.n().scale(f * length));
                Vec3 p = spine.add(b.u().scale(Math.cos(angle) * radius))
                        .add(b.v().scale(Math.sin(angle) * radius));
                send(level, particle, p, 1, 0.0, 0.0, 0.0, speed);
            }
        }
    }

    public static void column(ServerLevel level, ParticleOptions particle, Vec3 baseCenter,
                              double height, double radius, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double f = i / (double) Math.max(1, count);
            double angle = GOLDEN_ANGLE * i;
            double r = radius * (0.35 + 0.65 * f);
            Vec3 p = baseCenter.add(Math.cos(angle) * r, f * height, Math.sin(angle) * r);
            send(level, particle, p, 1, 0.0, 0.04, 0.0, speed);
        }
    }
    // #endregion
}
