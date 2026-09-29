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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — CUSTOM 3D LIGHTNING VFX (3D STORM CLOUD, RUNE, BOLT & ARC)
// ============================================================================
public final class LightningFx {

    // #region 2. ELECTRIC SCHOOL & THUNDERCLOUD COLOR PALETTE
    private static final int COLOR_WHITE_CORE = 0xF1FCFD;
    private static final int COLOR_ELECTRIC_CYAN = 0x73DAFC;
    private static final int COLOR_STORM_AZURE = 0x3692FC;
    private static final int COLOR_DEEP_COBALT = 0x235698;
    private static final int COLOR_CLOUD_DARK = 0x141E30;
    private static final int COLOR_CLOUD_SLATE = 0x21324C;
    private static final int COLOR_CLOUD_RIM = 0x314B70;
    private static final Vec3 UP_AXIS = new Vec3(0.0, 1.0, 0.0);

    // 9 Layered 3D Voxel Cloud Lobes: {offsetX, offsetY, offsetZ, halfWidth, halfHeight, halfDepth}
    private static final float[][] CLOUD_LOBES = {
            { 0.00F,  0.00F,  0.00F, 1.85F, 0.48F, 1.65F}, // Main central thunderhead body
            { 0.00F,  0.46F,  0.00F, 1.25F, 0.38F, 1.15F}, // Upper storm crown
            {-1.35F, -0.06F,  0.25F, 1.05F, 0.40F, 1.05F}, // Left billowing puff
            { 1.35F, -0.04F, -0.20F, 1.10F, 0.42F, 1.00F}, // Right billowing puff
            { 0.25F, -0.08F,  1.25F, 1.05F, 0.38F, 0.95F}, // Front puff
            {-0.25F, -0.05F, -1.25F, 1.10F, 0.40F, 0.95F}, // Back puff
            {-0.85F,  0.34F, -0.55F, 0.85F, 0.32F, 0.85F}, // Upper-left anvil puff
            { 0.80F,  0.32F,  0.55F, 0.85F, 0.32F, 0.80F}, // Upper-right anvil puff
            { 0.00F, -0.36F,  0.00F, 1.35F, 0.24F, 1.25F}  // Charged lower anvil belly
    };

    private LightningFx() {}

    public static void spawnCharge(SpellFxPayload payload) {
        SpellFx.add(new ChargeConstruct(payload.start(), payload.end(), payload.radius(), payload.durationTicks()));
    }

    public static void spawnBolt(SpellFxPayload payload) {
        Vec3 spot = payload.start();
        Vec3 skyStart = payload.end();
        float radius = payload.radius();
        CameraShake.addAt(spot, 24.0, 0.38F, 13, 0.82F);
        ScreenFlash.triggerAt(spot, 18.0, COLOR_ELECTRIC_CYAN, 0.18F, 7);
        int seed = (int) (Double.doubleToLongBits(spot.x * 31.0 + spot.z * 17.0) ^ System.nanoTime());
        SpellFx.add(new BoltConstruct(spot, skyStart, radius, payload.durationTicks(), seed));
    }

    public static void spawnArc(SpellFxPayload payload) {
        Vec3 from = payload.start();
        Vec3 to = payload.end();
        int seed = (int) (Double.doubleToLongBits(from.x * 19.0 + to.z * 23.0) ^ System.nanoTime());
        SpellFx.add(new ArcConstruct(from, to, payload.durationTicks(), seed));
    }
    // #endregion

    // #region 3. PROCEDURAL 3D VANILLA+ VOXEL THUNDERCLOUD RENDERER
    private static void renderThundercloud(PoseStack poseStack, VertexConsumer vc, Vec3 cloudCenter,
                                           float scale, float alpha, float flashIntensity, float time, int seed) {
        if (alpha <= 0.01F || scale <= 0.02F) {
            return;
        }
        float driftRot = time * 0.025F;

        // 1. Multi-Layered 3D Voxel Cloud Blocks
        for (int i = 0; i < CLOUD_LOBES.length; i++) {
            float[] lobe = CLOUD_LOBES[i];
            double bob = Math.sin(time * 0.22 + i * 1.1) * 0.06 * scale;
            Vec3 pos = cloudCenter.add(lobe[0] * scale, lobe[1] * scale + bob, lobe[2] * scale);
            float hx = lobe[3] * scale;
            float hy = lobe[4] * scale;
            float hz = lobe[5] * scale;

            int baseCol = (i == 1 || i == 6 || i == 7) ? COLOR_CLOUD_RIM
                    : (i == 0 || i == 8) ? COLOR_CLOUD_DARK : COLOR_CLOUD_SLATE;
            ConstructPainter.voxelBox(poseStack, vc, pos, hx, hy, hz, driftRot,
                    ConstructPainter.color(baseCol, alpha * 0.88F));

            // Inner Electric Glow Core pulsing inside each cloud puff
            if (flashIntensity > 0.02F) {
                int glowCol = (i == 8 || i == 0) ? COLOR_ELECTRIC_CYAN : COLOR_STORM_AZURE;
                ConstructPainter.voxelBox(poseStack, vc, pos.subtract(0.0, 0.06 * scale, 0.0),
                        hx * 0.78F, hy * 0.72F, hz * 0.78F, driftRot,
                        ConstructPainter.color(glowCol, alpha * flashIntensity * 0.58F));
            }
        }

        // 2. Bright Electric Heart inside the Cloud Belly
        if (flashIntensity > 0.05F) {
            Vec3 belly = cloudCenter.subtract(0.0, 0.32 * scale, 0.0);
            ConstructPainter.voxelBox(poseStack, vc, belly,
                    0.95F * scale, 0.22F * scale, 0.90F * scale, -driftRot * 1.5F,
                    ConstructPainter.color(COLOR_WHITE_CORE, alpha * flashIntensity * 0.85F));
            ConstructPainter.ring(poseStack, vc, belly.subtract(0.0, 0.12 * scale, 0.0), UP_AXIS,
                    0.65F * scale, 1.55F * scale, 24,
                    ConstructPainter.color(COLOR_ELECTRIC_CYAN, alpha * flashIntensity * 0.65F));
        }

        // 3. Crackling Horizontal Mini-Arcs Crawling Under the Storm Cloud
        float flicker = (float) Math.floor(time * 1.8F);
        for (int a = 0; a < 4; a++) {
            double ang0 = (Math.PI * 0.5 * a) + Noise.of(seed + a * 19, flicker * 0.4F, 11) * 0.6;
            double ang1 = ang0 + 0.9 + Noise.of(seed + a * 19, flicker * 0.4F, 29) * 0.5;
            double r0 = (0.35 + (a % 2) * 0.35) * scale;
            double r1 = (1.35 + ((a + 1) % 2) * 0.45) * scale;
            Vec3 c0 = cloudCenter.add(Math.cos(ang0) * r0, -0.42 * scale, Math.sin(ang0) * r0);
            Vec3 c1 = cloudCenter.add(Math.cos(ang1) * r1, -0.36 * scale, Math.sin(ang1) * r1);
            Vec3 mid = c0.lerp(c1, 0.5).add(
                    Noise.of(seed + a * 37, flicker, 43) * 0.35 * scale,
                    -0.15 * scale,
                    Noise.of(seed + a * 37, flicker, 71) * 0.35 * scale
            );
            float arcAlpha = alpha * (0.45F + 0.50F * flashIntensity);
            ConstructPainter.glowTaper(poseStack, vc, c0, mid, 0.055F * scale, 0.045F * scale,
                    COLOR_WHITE_CORE, COLOR_ELECTRIC_CYAN, arcAlpha);
            ConstructPainter.glowTaper(poseStack, vc, mid, c1, 0.045F * scale, 0.025F * scale,
                    COLOR_WHITE_CORE, COLOR_STORM_AZURE, arcAlpha);
        }
    }
    // #endregion

    // #region 4. SKY-RUNE & SWELLING STORM CLOUD CHARGE CONSTRUCT
    private static final class ChargeConstruct implements SpellFx.Construct {
        private final Vec3 groundSpot;
        private final Vec3 skySpot;
        private final float maxRadius;
        private final int maxTicks;
        private int ageTicks;

        ChargeConstruct(Vec3 groundSpot, Vec3 skySpot, float maxRadius, int maxTicks) {
            this.groundSpot = groundSpot;
            this.skySpot = skySpot;
            this.maxRadius = Math.max(1.2F, maxRadius);
            this.maxTicks = Math.max(2, maxTicks);
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
            float alpha = (float) Math.sin(prog * Math.PI * 0.85 + 0.15) * 0.92F;
            if (alpha <= 0.01F) {
                return;
            }

            // 1. Swelling 3D Storm Cloud Overhead
            float cloudScale = (float) Ease.outBack(Math.min(1.0, prog * 1.25));
            renderThundercloud(poseStack, vc, this.skySpot, cloudScale, alpha,
                    0.35F + 0.65F * prog, exactAge, 1337);

            // 2. Ground Target-Lock Electric Rune
            Vec3 base = this.groundSpot.add(0.0, 0.06, 0.0);
            float runeR = this.maxRadius * (0.68F - 0.30F * prog);
            float spin = exactAge * 0.32F;

            ConstructPainter.ring(poseStack, vc, base, UP_AXIS, runeR * 0.88F, runeR, 28,
                    ConstructPainter.color(COLOR_ELECTRIC_CYAN, alpha * 0.85F));
            ConstructPainter.ring(poseStack, vc, base.add(0.0, 0.02, 0.0), UP_AXIS, runeR * 0.42F, runeR * 0.50F, 20,
                    ConstructPainter.color(COLOR_WHITE_CORE, alpha * 0.92F));

            for (int i = 0; i < 4; i++) {
                double a0 = spin + i * (Math.PI * 0.5);
                double a1 = spin + (i + 1) * (Math.PI * 0.5);
                Vec3 p0 = base.add(Math.cos(a0) * runeR * 0.86, 0.03, Math.sin(a0) * runeR * 0.86);
                Vec3 p1 = base.add(Math.cos(a1) * runeR * 0.86, 0.03, Math.sin(a1) * runeR * 0.86);
                ConstructPainter.lightTaper(poseStack, vc, p0, p1, 0.045F, 0.045F,
                        COLOR_WHITE_CORE, COLOR_STORM_AZURE, alpha * 0.85F);

                Vec3 nodePos = base.add(Math.cos(a0) * runeR * 0.65, 0.18 + prog * 0.85, Math.sin(a0) * runeR * 0.65);
                ConstructPainter.voxelCube(poseStack, vc, nodePos, 0.065F, spin * 1.4F, -spin, spin * 0.8F,
                        ConstructPainter.color(COLOR_ELECTRIC_CYAN, alpha));
            }

            // 3. Converging Leader Filament from Cloud Belly to Ground Rune
            float beamRadius = 0.025F + 0.055F * prog;
            ConstructPainter.lightTaper(poseStack, vc, this.skySpot.subtract(0.0, 0.35, 0.0), base,
                    beamRadius * 0.5F, beamRadius,
                    COLOR_WHITE_CORE, COLOR_ELECTRIC_CYAN, alpha * (0.35F + 0.55F * prog));
        }
    }
    // #endregion

    // #region 5. 3D STORM CLOUD, MULTI-BRANCHED LIGHTNING BOLT & SHOCKWAVE CONSTRUCT
    private static final class BoltConstruct implements SpellFx.Construct {
        private final Vec3 spot;
        private final Vec3 skyStart;
        private final float shockRadius;
        private final int maxTicks;
        private final int seed;
        private int ageTicks;

        BoltConstruct(Vec3 spot, Vec3 skyStart, float shockRadius, int maxTicks, int seed) {
            this.spot = spot;
            this.skyStart = skyStart;
            this.shockRadius = Math.max(1.5F, shockRadius);
            this.maxTicks = Math.max(12, maxTicks);
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
            float boltFade = Mth.clamp(1.0F - (exactAge / (this.maxTicks * 0.68F)), 0.0F, 1.0F);
            float cloudAlpha = Mth.clamp(1.0F - ((prog - 0.55F) / 0.45F), 0.0F, 1.0F);
            float cloudScale = 1.05F + 0.15F * prog;

            // 1. Render Overhead 3D Thundercloud Firing the Bolt
            renderThundercloud(poseStack, vc, this.skyStart, cloudScale, cloudAlpha,
                    boltFade, exactAge, this.seed);

            int detail = ClientSettings.get(ClientSettings.EFFECT_DETAIL);
            int segments = detail == 0 ? 10 : 14;
            float flickerStep = (float) Math.floor(exactAge * 1.75F);

            // 2. Main Sky-to-Ground 3D Jagged Lightning Bolt + 5 Branching Forks
            if (boltFade > 0.01F) {
                Vec3 boltOrigin = this.skyStart.subtract(0.0, 0.35, 0.0);
                Vec3 delta = this.spot.subtract(boltOrigin);
                Vectors.Basis basis = Vectors.basis(delta.normalize());

                Vec3[] spine = new Vec3[segments + 1];
                spine[0] = boltOrigin;
                spine[segments] = this.spot.add(0.0, 0.12, 0.0);

                for (int i = 1; i < segments; i++) {
                    double f = (double) i / segments;
                    Vec3 basePt = boltOrigin.lerp(spine[segments], f);
                    double env = Math.sin(f * Math.PI);
                    double jU = Noise.of(this.seed, i * 1.7F + flickerStep * 0.35F, 13) * 0.95 * env;
                    double jV = Noise.of(this.seed, i * 1.7F + flickerStep * 0.35F, 41) * 0.95 * env;
                    spine[i] = basePt.add(basis.u().scale(jU)).add(basis.v().scale(jV));
                }

                float trunkThick = (0.20F * (0.45F + 0.55F * boltFade));
                for (int i = 0; i < segments; i++) {
                    float f0 = (float) i / segments;
                    float f1 = (float) (i + 1) / segments;
                    float r0 = trunkThick * (0.78F + 0.35F * f0);
                    float r1 = trunkThick * (0.78F + 0.35F * f1);
                    ConstructPainter.glowTaper(poseStack, vc, spine[i], spine[i + 1],
                            r0, r1, COLOR_WHITE_CORE, COLOR_STORM_AZURE, boltFade);
                    ConstructPainter.lightTaper(poseStack, vc, spine[i], spine[i + 1],
                            r0 * 0.65F, r1 * 0.65F, COLOR_WHITE_CORE, COLOR_ELECTRIC_CYAN, boltFade);
                }

                // 5 Procedural 3D Branching Lightning Forks
                int branchCount = detail == 0 ? 3 : 5;
                for (int b = 0; b < branchCount; b++) {
                    int idx = 2 + ((b * 2 + 1) % Math.max(2, segments - 4));
                    Vec3 branchOrigin = spine[idx];
                    Vec3 dir = Noise.direction(this.seed + b * 97, flickerStep * 0.25F + b);
                    Vec3 branchDir = new Vec3(dir.x * 1.3, -Math.abs(dir.y) - 0.65, dir.z * 1.3).normalize();
                    double branchLen = 2.0 + (b % 3) * 0.85;

                    Vec3 prev = branchOrigin;
                    int bSegs = 4;
                    for (int s = 1; s <= bSegs; s++) {
                        double sf = (double) s / bSegs;
                        Vec3 next = branchOrigin.add(branchDir.scale(branchLen * sf)).add(
                                Noise.of(this.seed + b * 31, s * 2.1F + flickerStep * 0.4F, 19) * 0.38,
                                Noise.of(this.seed + b * 31, s * 2.1F + flickerStep * 0.4F, 53) * 0.22,
                                Noise.of(this.seed + b * 31, s * 2.1F + flickerStep * 0.4F, 89) * 0.38
                        );
                        float br0 = trunkThick * 0.52F * (float) (1.0 - (s - 1.0) / bSegs);
                        float br1 = trunkThick * 0.52F * (float) (1.0 - sf * 0.85);
                        ConstructPainter.glowTaper(poseStack, vc, prev, next,
                                br0, br1, COLOR_WHITE_CORE, COLOR_ELECTRIC_CYAN, boltFade * 0.85F);
                        prev = next;
                    }
                }
            }

            // 3. Impact 3D Tumbling Plasma Voxel Core
            float coreFade = (float) Math.pow(1.0F - prog, 1.35);
            Vec3 coreCenter = this.spot.add(0.0, 0.38, 0.0);
            float spin = exactAge * 0.42F;
            float coreScale = (float) Ease.outBack(Math.min(1.0, exactAge / 3.5)) * (1.0F - 0.55F * prog);

            ConstructPainter.voxelCube(poseStack, vc, coreCenter, 0.34F * coreScale,
                    spin, -spin * 1.2F, spin * 0.7F,
                    ConstructPainter.color(COLOR_WHITE_CORE, coreFade * 0.95F));
            ConstructPainter.voxelCube(poseStack, vc, coreCenter, 0.52F * coreScale,
                    -spin * 0.8F + 0.5F, spin * 0.9F, -spin * 1.1F,
                    ConstructPainter.color(COLOR_ELECTRIC_CYAN, coreFade * 0.62F));
            ConstructPainter.voxelCube(poseStack, vc, coreCenter, 0.68F * coreScale,
                    spin * 0.6F, spin * 0.5F + 0.8F, -spin * 0.6F,
                    ConstructPainter.color(COLOR_STORM_AZURE, coreFade * 0.34F));

            // 4. Double Expanding 3D Ground Electric Shockwave Rings
            float ringExpand = (float) Ease.outCubic(prog);
            float outerR = this.shockRadius * (0.18F + 0.92F * ringExpand);
            float innerR = Math.max(0.05F, outerR - 0.32F * (1.0F - 0.55F * prog));
            Vec3 ringCenter = this.spot.add(0.0, 0.08, 0.0);

            ConstructPainter.ring(poseStack, vc, ringCenter, UP_AXIS, innerR, outerR, 28,
                    ConstructPainter.color(COLOR_ELECTRIC_CYAN, coreFade * 0.85F));
            ConstructPainter.ring(poseStack, vc, ringCenter.add(0.0, 0.02, 0.0), UP_AXIS,
                    innerR * 0.55F, outerR * 0.62F, 22,
                    ConstructPainter.color(COLOR_WHITE_CORE, coreFade * 0.72F));

            // 5. 10 Ballistic 3D Electric Voxel Sparks
            int sparkCount = detail == 0 ? 6 : 10;
            for (int i = 0; i < sparkCount; i++) {
                double angle = (Math.PI * 2.0 * i) / sparkCount + (this.seed & 7) * 0.25;
                double dist = this.shockRadius * (0.25 + 0.78 * ringExpand) * (0.75 + 0.25 * ((i % 3) * 0.5));
                double arcY = Math.sin(prog * Math.PI) * (0.95 + (i % 3) * 0.42);
                Vec3 sparkPos = this.spot.add(Math.cos(angle) * dist, 0.20 + arcY, Math.sin(angle) * dist);
                float cubeSize = 0.085F * (1.0F - 0.65F * prog);
                int col = (i % 2 == 0) ? COLOR_WHITE_CORE : COLOR_ELECTRIC_CYAN;
                ConstructPainter.voxelCube(poseStack, vc, sparkPos, cubeSize,
                        spin * 1.6F + i, -spin * 1.3F + i, spin + i,
                        ConstructPainter.color(col, coreFade * 0.92F));
            }
        }
    }
    // #endregion

    // #region 6. 3D JAGGED CHAIN-LIGHTNING ARC CONSTRUCT
    private static final class ArcConstruct implements SpellFx.Construct {
        private final Vec3 from;
        private final Vec3 to;
        private final int maxTicks;
        private final int seed;
        private int ageTicks;

        ArcConstruct(Vec3 from, Vec3 to, int maxTicks, int seed) {
            this.from = from;
            this.to = to;
            this.maxTicks = Math.max(4, maxTicks);
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
            float fade = Mth.clamp(1.0F - (exactAge / (float) this.maxTicks), 0.0F, 1.0F);
            if (fade <= 0.01F) {
                return;
            }

            Vec3 delta = this.to.subtract(this.from);
            if (delta.lengthSqr() < 1.0E-4) {
                return;
            }
            Vectors.Basis basis = Vectors.basis(delta.normalize());
            int segs = 7;
            float flicker = (float) Math.floor(exactAge * 2.0F);

            Vec3 prev = this.from;
            for (int i = 1; i <= segs; i++) {
                double f = (double) i / segs;
                Vec3 next;
                if (i == segs) {
                    next = this.to;
                } else {
                    double env = Math.sin(f * Math.PI);
                    double jU = Noise.of(this.seed, i * 2.3F + flicker * 0.5F, 17) * 0.42 * env;
                    double jV = Noise.of(this.seed, i * 2.3F + flicker * 0.5F, 59) * 0.42 * env;
                    next = this.from.lerp(this.to, f).add(basis.u().scale(jU)).add(basis.v().scale(jV));
                }
                float r = 0.085F * (0.55F + 0.45F * fade);
                ConstructPainter.glowTaper(poseStack, vc, prev, next, r, r,
                        COLOR_WHITE_CORE, COLOR_ELECTRIC_CYAN, fade);
                prev = next;
            }

            float spin = exactAge * 0.55F;
            ConstructPainter.voxelCube(poseStack, vc, this.to, 0.14F * fade,
                    spin, -spin * 1.2F, spin * 0.8F,
                    ConstructPainter.color(COLOR_WHITE_CORE, fade * 0.90F));
            ConstructPainter.voxelCube(poseStack, vc, this.to, 0.22F * fade,
                    -spin, spin * 0.9F, -spin * 1.1F,
                    ConstructPainter.color(COLOR_DEEP_COBALT, fade * 0.55F));
        }
    }
    // #endregion
}
