package com.magicmadness.engine.client.render;

// #region 1. IMPORTS
import com.magicmadness.engine.math.Vectors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
// #endregion

// ============================================================================
// MAGIC MADNESS — PROCEDURAL 3D VANILLA+ VOXEL & CONSTRUCT PAINTER
// ============================================================================
public final class ConstructPainter {

    // #region 2. RENDER LAYER & COLOR PACKING
    private static final RenderType CONSTRUCT_LAYER = RenderType.lightning();

    private ConstructPainter() {}

    public static VertexConsumer buffer(MultiBufferSource source) {
        return source.getBuffer(CONSTRUCT_LAYER);
    }

    public static int color(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return FastColor.ARGB32.color(a, r, g, b);
    }
    // #endregion

    // #region 3. 3D VANILLA+ ROTATED VOXEL CUBES
    public static void voxelCube(PoseStack poseStack, VertexConsumer vc, Vec3 center,
                                 float halfSize, float rotX, float rotY, float rotZ, int argb) {
        if (halfSize <= 0.002F) {
            return;
        }
        Matrix4f mat = poseStack.last().pose();
        Quaternionf q = new Quaternionf().rotateXYZ(rotX, rotY, rotZ);

        Vec3 p000 = rotateCorner(center, q, -halfSize, -halfSize, -halfSize);
        Vec3 p100 = rotateCorner(center, q,  halfSize, -halfSize, -halfSize);
        Vec3 p110 = rotateCorner(center, q,  halfSize,  halfSize, -halfSize);
        Vec3 p010 = rotateCorner(center, q, -halfSize,  halfSize, -halfSize);

        Vec3 p001 = rotateCorner(center, q, -halfSize, -halfSize,  halfSize);
        Vec3 p101 = rotateCorner(center, q,  halfSize, -halfSize,  halfSize);
        Vec3 p111 = rotateCorner(center, q,  halfSize,  halfSize,  halfSize);
        Vec3 p011 = rotateCorner(center, q, -halfSize,  halfSize,  halfSize);

        // 6 Cube Faces (Double-Sided for Translucent Additive Layer)
        emitQuadDoubleSided(mat, vc, p001, p101, p111, p011, argb, argb); // Front
        emitQuadDoubleSided(mat, vc, p100, p000, p010, p110, argb, argb); // Back
        emitQuadDoubleSided(mat, vc, p011, p111, p110, p010, argb, argb); // Top
        emitQuadDoubleSided(mat, vc, p000, p100, p101, p001, argb, argb); // Bottom
        emitQuadDoubleSided(mat, vc, p101, p100, p110, p111, argb, argb); // Right
        emitQuadDoubleSided(mat, vc, p000, p001, p011, p010, argb, argb); // Left
    }

    public static void voxelBox(PoseStack poseStack, VertexConsumer vc, Vec3 center,
                                float hx, float hy, float hz, float rotY, int argb) {
        if (hx <= 0.002F || hy <= 0.002F || hz <= 0.002F) {
            return;
        }
        Matrix4f mat = poseStack.last().pose();
        Quaternionf q = new Quaternionf().rotateY(rotY);

        Vec3 p000 = rotateCorner(center, q, -hx, -hy, -hz);
        Vec3 p100 = rotateCorner(center, q,  hx, -hy, -hz);
        Vec3 p110 = rotateCorner(center, q,  hx,  hy, -hz);
        Vec3 p010 = rotateCorner(center, q, -hx,  hy, -hz);

        Vec3 p001 = rotateCorner(center, q, -hx, -hy,  hz);
        Vec3 p101 = rotateCorner(center, q,  hx, -hy,  hz);
        Vec3 p111 = rotateCorner(center, q,  hx,  hy,  hz);
        Vec3 p011 = rotateCorner(center, q, -hx,  hy,  hz);

        emitQuadDoubleSided(mat, vc, p001, p101, p111, p011, argb, argb);
        emitQuadDoubleSided(mat, vc, p100, p000, p010, p110, argb, argb);
        emitQuadDoubleSided(mat, vc, p011, p111, p110, p010, argb, argb);
        emitQuadDoubleSided(mat, vc, p000, p100, p101, p001, argb, argb);
        emitQuadDoubleSided(mat, vc, p101, p100, p110, p111, argb, argb);
        emitQuadDoubleSided(mat, vc, p000, p001, p011, p010, argb, argb);
    }

    private static Vec3 rotateCorner(Vec3 center, Quaternionf q, float dx, float dy, float dz) {
        Vector3f v = new Vector3f(dx, dy, dz).rotate(q);
        return new Vec3(center.x + v.x, center.y + v.y, center.z + v.z);
    }
    // #endregion

    // #region 4. 3D BEAMS, TAPERS & RINGS
    public static void beam(PoseStack poseStack, VertexConsumer vc, Vec3 from, Vec3 to, float radius, int argb) {
        taper(poseStack, vc, from, to, radius, radius, argb, argb);
    }

    public static void taper(PoseStack poseStack, VertexConsumer vc, Vec3 from, Vec3 to,
                             float startRadius, float endRadius, int startArgb, int endArgb) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 1.0E-7) {
            return;
        }
        Vec3 forward = delta.normalize();
        Vec3 right = Vectors.perpendicular(forward);
        Vec3 up = forward.cross(right).normalize();

        Matrix4f mat = poseStack.last().pose();

        emitQuadDoubleSided(mat, vc,
                from.subtract(right.scale(startRadius)),
                from.add(right.scale(startRadius)),
                to.add(right.scale(endRadius)),
                to.subtract(right.scale(endRadius)),
                startArgb, endArgb);

        emitQuadDoubleSided(mat, vc,
                from.subtract(up.scale(startRadius)),
                from.add(up.scale(startRadius)),
                to.add(up.scale(endRadius)),
                to.subtract(up.scale(endRadius)),
                startArgb, endArgb);
    }

    public static void glowTaper(PoseStack poseStack, VertexConsumer vc, Vec3 from, Vec3 to,
                                 float startRadius, float endRadius, int coreRgb, int outerRgb, float alpha) {
        if (alpha <= 0.005F) {
            return;
        }
        int outerArgb = color(outerRgb, alpha * 0.20F);
        int midArgb = color(outerRgb, alpha * 0.48F);
        int coreArgb = color(coreRgb, Mth.clamp(alpha * 0.90F, 0.0F, 1.0F));

        taper(poseStack, vc, from, to, startRadius * 1.9F, endRadius * 1.9F, outerArgb, outerArgb);
        taper(poseStack, vc, from, to, startRadius * 1.25F, endRadius * 1.25F, midArgb, midArgb);
        taper(poseStack, vc, from, to, startRadius * 0.60F, endRadius * 0.60F, coreArgb, coreArgb);
    }

    public static void lightTaper(PoseStack poseStack, VertexConsumer vc, Vec3 from, Vec3 to,
                                  float startRadius, float endRadius, int coreRgb, int outerRgb, float alpha) {
        if (alpha <= 0.005F) {
            return;
        }
        int midArgb = color(outerRgb, alpha * 0.42F);
        int coreArgb = color(coreRgb, Mth.clamp(alpha * 0.85F, 0.0F, 1.0F));
        taper(poseStack, vc, from, to, startRadius * 1.3F, endRadius * 1.3F, midArgb, midArgb);
        taper(poseStack, vc, from, to, startRadius * 0.6F, endRadius * 0.6F, coreArgb, coreArgb);
    }

    public static void ring(PoseStack poseStack, VertexConsumer vc, Vec3 center, Vec3 axis,
                            float innerRadius, float outerRadius, int segments, int argb) {
        if (outerRadius <= 0.002F) {
            return;
        }
        Matrix4f mat = poseStack.last().pose();
        int segs = Math.max(6, segments);
        double step = (Math.PI * 2.0) / segs;

        for (int i = 0; i < segs; i++) {
            double a0 = i * step;
            double a1 = (i + 1) * step;
            Vec3 p0In = center.add(Vectors.circleOffset(axis, innerRadius, a0));
            Vec3 p1In = center.add(Vectors.circleOffset(axis, innerRadius, a1));
            Vec3 p1Out = center.add(Vectors.circleOffset(axis, outerRadius, a1));
            Vec3 p0Out = center.add(Vectors.circleOffset(axis, outerRadius, a0));
            emitQuadDoubleSided(mat, vc, p0In, p1In, p1Out, p0Out, argb, argb);
        }
    }

    private static void emitQuadDoubleSided(Matrix4f mat, VertexConsumer vc, Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3,
                                            int startArgb, int endArgb) {
        vc.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).setColor(startArgb);
        vc.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).setColor(startArgb);
        vc.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).setColor(endArgb);
        vc.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).setColor(endArgb);

        vc.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).setColor(endArgb);
        vc.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).setColor(endArgb);
        vc.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).setColor(startArgb);
        vc.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).setColor(startArgb);
    }
    // #endregion
}
