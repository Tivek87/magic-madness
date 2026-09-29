package com.magicmadness.spell.client;

// #region 1. IMPORTS
import com.magicmadness.config.client.ClientSettings;
import com.magicmadness.engine.client.fx.CameraShake;
import com.magicmadness.engine.client.fx.ScreenFlash;
import com.magicmadness.engine.client.render.ConstructPainter;
import com.magicmadness.engine.math.Ease;
import com.magicmadness.engine.math.Noise;
import com.magicmadness.engine.math.Vectors;
import com.magicmadness.network.SpellFxPayload;
import com.magicmadness.spell.fire.FireballSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — VANILLA+ 3D VOXEL FIREBALL VFX (FLIGHT, BLAST & ASH)
// ============================================================================
public final class FireFx {

    // #region 2. VANILLA+ BLAZE & MAGMA COLOR PALETTE
    private static final int COLOR_CREAM_CORE = 0xFFF6C4;
    private static final int COLOR_BLAZE_GOLD = 0xFFAE2B;
    private static final int COLOR_FLAME_ORANGE = 0xF26419;
    private static final int COLOR_MAGMA_CRIMSON = 0xB82612;

    private FireFx() {}

    public static void spawnFlight(SpellFxPayload payload) {
        SpellFx.add(new FlightConstruct(
                payload.casterId(),
                payload.start(),
                payload.end(),
                payload.radius(),
                payload.durationTicks()
        ));
    }

    public static void spawnBlast(SpellFxPayload payload) {
        SpellFx.stopFlightForCaster(payload.casterId());
        Vec3 pos = payload.start();
        float radius = payload.radius();
        CameraShake.addAt(pos, 18.0, 0.26F, 10, 0.75F);
        ScreenFlash.triggerAt(pos, 14.0, COLOR_FLAME_ORANGE, 0.12F, 6);
        SpellFx.add(new BlastConstruct(pos, radius, payload.durationTicks()));
    }

    public static void spawnAsh(SpellFxPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == payload.casterId()) {
            SpellRadialMenu.triggerKillConfirm();
        }
        if (ClientSettings.get(ClientSettings.ASH_DISINTEGRATION) == 1) {
            SpellFx.add(new AshConstruct(payload.start(), payload.end(), payload.durationTicks()));
        }
    }
    // #endregion

    // #region 3. VANILLA+ TUMBLING 3D VOXEL FIREBALL & EMBER RIBBON TRAIL
    private static final class FlightConstruct implements SpellFx.Construct {
        private final int casterId;
        private final Vec3 start;
        private final Vec3 initialVel;
        private final double gravity;
        private final int maxTicks;
        private int ageTicks;

        FlightConstruct(int casterId, Vec3 start, Vec3 initialVel, double gravity, int maxTicks) {
            this.casterId = casterId;
            this.start = start;
            this.initialVel = initialVel;
            this.gravity = gravity;
            this.maxTicks = Math.max(4, maxTicks);
        }

        @Override
        public int casterId() {
            return this.casterId;
        }

        @Override
        public boolean isFlightConstruct() {
            return true;
        }

        @Override
        public boolean tick() {
            this.ageTicks++;
            return this.ageTicks <= this.maxTicks;
        }

        @Override
        public void render(PoseStack poseStack, VertexConsumer vc, float partialTick, float unused) {
            double exactTick = Math.min(this.maxTicks, this.ageTicks + partialTick);
            Vec3 headPos = FireballSpell.evaluateArcPosition(this.start, this.initialVel, this.gravity, exactTick);

            float spin = (float) (exactTick * 0.38);

            // 1. Tumbling Vanilla+ 3D Voxel Fireball Core (Inner Cream Cube + Counter-Rotating Blaze Shell)
            ConstructPainter.voxelCube(poseStack, vc, headPos, 0.12F,
                    spin, spin * 1.3F, spin * 0.7F,
                    ConstructPainter.color(COLOR_CREAM_CORE, 0.96F));
            ConstructPainter.voxelCube(poseStack, vc, headPos, 0.18F,
                    -spin * 0.85F + 0.78F, spin * 0.95F, -spin * 0.6F + 0.78F,
                    ConstructPainter.color(COLOR_BLAZE_GOLD, 0.72F));
            ConstructPainter.voxelCube(poseStack, vc, headPos, 0.24F,
                    spin * 0.55F + 0.4F, -spin * 0.75F, spin * 0.45F,
                    ConstructPainter.color(COLOR_FLAME_ORANGE, 0.36F));

            // 2. Smooth Parabolic Ember Trail Following the Weighted Arc
            int detail = ClientSettings.get(ClientSettings.EFFECT_DETAIL);
            int trailPoints = detail == 0 ? 7 : 11;
            double trailSpanTicks = Math.min(exactTick, 3.2);
            if (trailSpanTicks > 0.08) {
                for (int i = 0; i < trailPoints - 1; i++) {
                    double f0 = (double) i / (trailPoints - 1);
                    double f1 = (double) (i + 1) / (trailPoints - 1);
                    double t0 = exactTick - f0 * trailSpanTicks;
                    double t1 = exactTick - f1 * trailSpanTicks;
                    Vec3 p0 = FireballSpell.evaluateArcPosition(this.start, this.initialVel, this.gravity, t0);
                    Vec3 p1 = FireballSpell.evaluateArcPosition(this.start, this.initialVel, this.gravity, t1);

                    float r0 = (float) Mth.lerp(f0, 0.11, 0.012);
                    float r1 = (float) Mth.lerp(f1, 0.11, 0.012);
                    float alpha = (float) Math.pow(1.0 - f0, 1.35) * 0.78F;

                    ConstructPainter.lightTaper(poseStack, vc, p0, p1, r0, r1,
                            COLOR_BLAZE_GOLD, COLOR_FLAME_ORANGE, alpha);
                }
            }

            // 3. Two Subtle Orbiting Voxel Ember Motes Around the Projectile
            if (detail > 0) {
                for (int m = 0; m < 2; m++) {
                    double angle = exactTick * 0.85 + m * Math.PI;
                    Vec3 motePos = headPos.add(
                            Math.cos(angle) * 0.26,
                            Math.sin(angle * 1.3) * 0.14,
                            Math.sin(angle) * 0.26
                    );
                    ConstructPainter.voxelCube(poseStack, vc, motePos, 0.045F,
                            spin * 1.5F, -spin, 0.4F,
                            ConstructPainter.color(COLOR_CREAM_CORE, 0.85F));
                }
            }
        }
    }
    // #endregion

    // #region 4. VANILLA+ 3D VOXEL BLAST FLASH, SHOCKWAVE RINGS & FLYING EMBER CUBES
    private static final class BlastConstruct implements SpellFx.Construct {
        private final Vec3 center;
        private final float blastRadius;
        private final int durationTicks;
        private int ageTicks;

        BlastConstruct(Vec3 center, float blastRadius, int durationTicks) {
            this.center = center;
            this.blastRadius = blastRadius;
            this.durationTicks = Math.max(10, durationTicks);
        }

        @Override
        public boolean tick() {
            this.ageTicks++;
            return this.ageTicks < this.durationTicks;
        }

        @Override
        public void render(PoseStack poseStack, VertexConsumer vc, float partialTick, float unused) {
            float exactAge = this.ageTicks + partialTick;
            float progress = Mth.clamp(exactAge / (float) this.durationTicks, 0.0F, 1.0F);

            // 1. Expanding Tilted 3D Voxel Blast Cubes (Vanilla+ Blocky Detonation Core)
            if (progress < 0.55F) {
                float coreT = progress / 0.55F;
                float expand = (float) Ease.outCubic(coreT);
                float fade = (1.0F - coreT) * (1.0F - coreT);
                float halfSize = this.blastRadius * 0.34F * (0.35F + 0.65F * expand);

                ConstructPainter.voxelCube(poseStack, vc, this.center.add(0.0, 0.25, 0.0),
                        halfSize * 0.65F, coreT * 0.8F, coreT * 1.1F, 0.4F,
                        ConstructPainter.color(COLOR_CREAM_CORE, fade * 0.88F));
                ConstructPainter.voxelCube(poseStack, vc, this.center.add(0.0, 0.25, 0.0),
                        halfSize, -coreT * 0.6F + 0.78F, coreT * 0.9F, 0.78F,
                        ConstructPainter.color(COLOR_BLAZE_GOLD, fade * 0.62F));
                ConstructPainter.voxelCube(poseStack, vc, this.center.add(0.0, 0.25, 0.0),
                        halfSize * 1.32F, 0.5F, -coreT * 0.7F, coreT * 0.5F,
                        ConstructPainter.color(COLOR_FLAME_ORANGE, fade * 0.34F));
            }

            // 2. Crisp Ground Shockwave Ring (12-segment blocky polygon ring)
            if (progress < 0.65F) {
                float ringT = progress / 0.65F;
                float outerR = this.blastRadius * 1.05F * (float) Ease.outCubic(ringT);
                float innerR = Math.max(0.05F, outerR - 0.24F * (1.0F - ringT * 0.5F));
                float ringAlpha = (1.0F - ringT) * 0.75F;
                ConstructPainter.ring(poseStack, vc, this.center.add(0.0, 0.08, 0.0), Vectors.UP,
                        innerR, outerR, 12, ConstructPainter.color(COLOR_BLAZE_GOLD, ringAlpha));
            }

            // 3. 8 Arcing 3D Voxel Ember Sparks Popping Outward with Gravity
            int sparks = ClientSettings.get(ClientSettings.EFFECT_DETAIL) == 0 ? 4 : 8;
            if (progress < 0.80F) {
                float sparkT = progress / 0.80F;
                float sparkAlpha = (1.0F - sparkT) * 0.90F;
                for (int i = 0; i < sparks; i++) {
                    double yaw = (Math.PI * 2.0 * i) / sparks + (i % 2) * 0.25;
                    double dist = this.blastRadius * (0.25 + 0.85 * sparkT);
                    double arcY = 0.25 + 1.35 * sparkT - 1.45 * sparkT * sparkT + (i % 3) * 0.12;
                    Vec3 sparkPos = this.center.add(Math.cos(yaw) * dist, Math.max(0.08, arcY), Math.sin(yaw) * dist);
                    float cubeSize = 0.065F * (1.0F - sparkT * 0.5F);
                    ConstructPainter.voxelCube(poseStack, vc, sparkPos, cubeSize,
                            exactAge * 0.35F + i, exactAge * 0.45F, i * 0.7F,
                            ConstructPainter.color(i % 2 == 0 ? COLOR_CREAM_CORE : COLOR_BLAZE_GOLD, sparkAlpha));
                }
            }
        }
    }
    // #endregion

    // #region 5. VANILLA+ RISING VOXEL EMBER CUBES ON ENEMY DEFEAT
    private static final class AshConstruct implements SpellFx.Construct {
        private final Vec3 basePos;
        private final double width;
        private final double height;
        private final int durationTicks;
        private int ageTicks;

        AshConstruct(Vec3 basePos, Vec3 dims, int durationTicks) {
            this.basePos = basePos;
            this.width = Math.max(0.5, dims.x);
            this.height = Math.max(1.0, dims.y);
            this.durationTicks = Math.max(12, durationTicks);
        }

        @Override
        public boolean tick() {
            this.ageTicks++;
            return this.ageTicks < this.durationTicks;
        }

        @Override
        public void render(PoseStack poseStack, VertexConsumer vc, float partialTick, float unused) {
            float exactAge = this.ageTicks + partialTick;
            float progress = Mth.clamp(exactAge / (float) this.durationTicks, 0.0F, 1.0F);
            float alpha = (1.0F - progress) * 0.85F;

            int emberCount = 7;
            for (int i = 0; i < emberCount; i++) {
                double angle = (Math.PI * 2.0 * i) / emberCount + progress * 1.4;
                double r = this.width * (0.30 + 0.35 * progress);
                double y = (this.height * (i / (double) emberCount)) * (1.0 - progress * 0.25) + progress * 1.15;
                Vec3 pos = this.basePos.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
                ConstructPainter.voxelCube(poseStack, vc, pos, 0.05F * (1.0F - progress * 0.4F),
                        exactAge * 0.25F + i, exactAge * 0.35F, 0.5F,
                        ConstructPainter.color(i % 2 == 0 ? COLOR_BLAZE_GOLD : COLOR_FLAME_ORANGE, alpha));
            }
        }
    }
    // #endregion
}
