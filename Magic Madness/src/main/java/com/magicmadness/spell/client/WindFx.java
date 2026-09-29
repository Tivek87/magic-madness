package com.magicmadness.spell.client;

// #region 1. IMPORTS
import com.magicmadness.config.client.ClientSettings;
import com.magicmadness.engine.client.fx.CameraShake;
import com.magicmadness.engine.client.fx.ScreenFlash;
import com.magicmadness.engine.client.render.ConstructPainter;
import com.magicmadness.engine.math.Ease;
import com.magicmadness.engine.math.Vectors;
import com.magicmadness.network.SpellFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — CUSTOM 3D WIND GUST VFX (VORTEX RINGS, CRESCENT BLADES & SLIPSTREAMS)
// ============================================================================
public final class WindFx {

    // #region 2. AIR SCHOOL COLOR PALETTE
    private static final int COLOR_BREEZE_WHITE = 0xF1FCFB;
    private static final int COLOR_STORM_MINT = 0xA1F3E3;
    private static final int COLOR_GALE_TURQUOISE = 0x52C3B9;
    private static final int COLOR_DEEP_TEAL = 0x2D9391;

    private WindFx() {}

    public static void spawnGust(SpellFxPayload payload) {
        Vec3 start = payload.start();
        Vec3 end = payload.end();
        float range = payload.radius();
        CameraShake.addAt(start, 14.0, 0.22F, 9, 0.85F);
        ScreenFlash.triggerAt(start, 10.0, COLOR_STORM_MINT, 0.10F, 5);
        int seed = (int) (Double.doubleToLongBits(start.x * 29.0 + end.z * 17.0) ^ System.nanoTime());
        SpellFx.add(new GustConstruct(start, end, range, payload.durationTicks(), seed));
    }
    // #endregion

    // #region 3. 3D STORM VORTEX CONE, CRESCENT WIND BLADES & HELICAL SLIPSTREAM CONSTRUCT
    private static final class GustConstruct implements SpellFx.Construct {
        private final Vec3 start;
        private final Vec3 dir;
        private final float range;
        private final int maxTicks;
        private final int seed;
        private int ageTicks;

        GustConstruct(Vec3 start, Vec3 end, float range, int maxTicks, int seed) {
            this.start = start;
            Vec3 delta = end.subtract(start);
            this.dir = delta.lengthSqr() > 1.0E-4 ? delta.normalize() : new Vec3(0.0, 0.0, 1.0);
            this.range = Math.max(3.0F, range);
            this.maxTicks = Math.max(8, maxTicks);
            this.seed = seed;
        }

        @Override
        public boolean tick() {
            this.ageTicks++;
            return this.ageTicks <= this.maxTicks;
        }

        @Override
        public void render(PoseStack poseStack, VertexConsumer vc, float partialTick, float unused) {
            float exactAge = this.ageTicks + partialTick;
            float prog = Mth.clamp(exactAge / (float) this.maxTicks, 0.0F, 1.0F);
            float rush = (float) Ease.outCubic(prog);
            float fade = (float) Math.pow(1.0F - prog, 1.15);
            if (fade <= 0.01F) {
                return;
            }

            Vectors.Basis basis = Vectors.basis(this.dir);
            int detail = ClientSettings.get(ClientSettings.EFFECT_DETAIL);

            // 1. Hand Origin Muzzle Pressure Wave Ring
            float muzzleR = (0.35F + 0.85F * rush);
            float muzzleFade = Mth.clamp(1.0F - prog * 1.55F, 0.0F, 1.0F);
            if (muzzleFade > 0.01F) {
                ConstructPainter.ring(poseStack, vc, this.start.add(this.dir.scale(0.35)), this.dir,
                        muzzleR * 0.78F, muzzleR, 24,
                        ConstructPainter.color(COLOR_BREEZE_WHITE, muzzleFade * 0.88F));
            }

            // 2. 4 Expanding 3D Storm Vortex Rings Rushing Along the Cone
            int ringCount = detail == 0 ? 3 : 4;
            for (int r = 0; r < ringCount; r++) {
                float rOffset = r * 0.16F;
                float rProg = Mth.clamp((prog - rOffset) / (1.0F - rOffset * 0.7F), 0.0F, 1.0F);
                if (rProg <= 0.001F) {
                    continue;
                }
                float rEase = (float) Ease.outCubic(rProg);
                double dist = (0.65 + rEase * (this.range * 0.92));
                float outerRadius = 0.48F + rEase * (1.85F + r * 0.24F);
                float thickness = 0.18F * (1.0F - 0.45F * rProg);
                float ringAlpha = fade * (1.0F - r * 0.14F) * 0.85F;

                Vec3 ringCenter = this.start.add(this.dir.scale(dist));
                int col = (r % 2 == 0) ? COLOR_BREEZE_WHITE : COLOR_STORM_MINT;
                ConstructPainter.ring(poseStack, vc, ringCenter, this.dir,
                        Math.max(0.05F, outerRadius - thickness), outerRadius, 26,
                        ConstructPainter.color(col, ringAlpha));
            }

            // 3. 3 Sweeping 3D Curved Wind-Crescent Blades Slicing Forward
            int bladeCount = detail == 0 ? 2 : 3;
            for (int b = 0; b < bladeCount; b++) {
                float bladeDelay = b * 0.11F;
                float bProg = Mth.clamp((prog - bladeDelay) / (1.0F - bladeDelay), 0.0F, 1.0F);
                if (bProg <= 0.01F) {
                    continue;
                }
                float bEase = (float) Ease.outCubic(bProg);
                double bladeDist = 0.8 + bEase * (this.range * 0.96);
                float bladeRadius = 0.65F + bEase * 1.95F;
                double baseRoll = (b * (Math.PI * 2.0 / bladeCount)) + exactAge * 0.28 + (this.seed & 7) * 0.3;
                double arcSpan = 1.35; // ~77 degree crescent blade arc
                int arcSegs = 8;

                Vec3 bladeCenter = this.start.add(this.dir.scale(bladeDist));
                Vec3 prevPt = null;
                for (int s = 0; s <= arcSegs; s++) {
                    double sf = (double) s / arcSegs;
                    double ang = baseRoll + (sf - 0.5) * arcSpan;
                    // Crescent curves slightly backward at its wingtips
                    double tipSweepBack = Math.pow((sf - 0.5) * 2.0, 2.0) * (0.45 + bEase * 0.55);
                    Vec3 pt = bladeCenter
                            .subtract(this.dir.scale(tipSweepBack))
                            .add(basis.u().scale(Math.cos(ang) * bladeRadius))
                            .add(basis.v().scale(Math.sin(ang) * bladeRadius));

                    if (prevPt != null) {
                        float env0 = (float) Math.sin(((s - 1.0) / arcSegs) * Math.PI);
                        float env1 = (float) Math.sin(sf * Math.PI);
                        float w0 = Math.max(0.02F, 0.14F * env0 * (1.0F - 0.35F * bProg));
                        float w1 = Math.max(0.02F, 0.14F * env1 * (1.0F - 0.35F * bProg));
                        ConstructPainter.glowTaper(poseStack, vc, prevPt, pt, w0, w1,
                                COLOR_BREEZE_WHITE, COLOR_STORM_MINT, fade * 0.92F);
                        ConstructPainter.lightTaper(poseStack, vc, prevPt, pt, w0 * 0.55F, w1 * 0.55F,
                                COLOR_BREEZE_WHITE, COLOR_GALE_TURQUOISE, fade * 0.85F);
                    }
                    prevPt = pt;
                }
            }

            // 4. 6 Spiraling 3D Slipstream Helical Ribbons Corkscrewing Around the Cone
            int strandCount = detail == 0 ? 4 : 6;
            int strandSegs = detail == 0 ? 8 : 11;
            double maxStreamLen = this.range * (0.35 + 0.65 * rush);
            for (int st = 0; st < strandCount; st++) {
                double phase0 = (Math.PI * 2.0 * st) / strandCount - exactAge * 0.38;
                Vec3 prev = null;
                for (int i = 0; i <= strandSegs; i++) {
                    double f = (double) i / strandSegs;
                    double d = 0.35 + f * maxStreamLen;
                    double spiralR = (0.22 + f * 1.75) * (0.65 + 0.35 * rush);
                    double ang = phase0 + f * 4.2;
                    Vec3 pt = this.start.add(this.dir.scale(d))
                            .add(basis.u().scale(Math.cos(ang) * spiralR))
                            .add(basis.v().scale(Math.sin(ang) * spiralR));
                    if (prev != null) {
                        float taper = (float) Math.sin(f * Math.PI) * 0.055F * fade;
                        int outerCol = (st % 2 == 0) ? COLOR_STORM_MINT : COLOR_GALE_TURQUOISE;
                        ConstructPainter.lightTaper(poseStack, vc, prev, pt, taper, taper * 0.85F,
                                COLOR_BREEZE_WHITE, outerCol, fade * 0.78F);
                    }
                    prev = pt;
                }
            }

            // 5. 14 Tumbling 3D Wind-Voxel Cubes Caught in the Gale Vortex
            int cubeCount = detail == 0 ? 8 : 14;
            float spin = exactAge * 0.45F;
            for (int c = 0; c < cubeCount; c++) {
                double cf = (c + 0.5) / cubeCount;
                double cDist = (0.5 + cf * this.range * 0.95) * rush;
                double cRadius = (0.25 + cf * 1.85) * (0.55 + 0.45 * rush);
                double cAngle = (c * 2.39996) + exactAge * (0.35 + (c % 3) * 0.08);
                Vec3 cubePos = this.start.add(this.dir.scale(cDist))
                        .add(basis.u().scale(Math.cos(cAngle) * cRadius))
                        .add(basis.v().scale(Math.sin(cAngle) * cRadius));
                float cubeSize = (0.065F + (c % 3) * 0.022F) * fade;
                int cubeCol = (c % 3 == 0) ? COLOR_BREEZE_WHITE
                        : (c % 3 == 1) ? COLOR_STORM_MINT : COLOR_DEEP_TEAL;
                ConstructPainter.voxelCube(poseStack, vc, cubePos, cubeSize,
                        spin + c, -spin * 1.2F + c, spin * 0.8F + c,
                        ConstructPainter.color(cubeCol, fade * 0.88F));
            }
        }
    }
    // #endregion
}
