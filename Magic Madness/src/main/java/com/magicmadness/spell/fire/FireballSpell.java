package com.magicmadness.spell.fire;

// #region 1. IMPORTS
import com.magicmadness.config.PowerRules;
import com.magicmadness.config.SpellConfig;
import com.magicmadness.engine.fx.ParticleFx;
import com.magicmadness.engine.math.Vectors;
import com.magicmadness.engine.tick.Effects;
import com.magicmadness.network.SpellFxPayload;
import com.magicmadness.spell.SpellTargets;
import com.magicmadness.spell.Targeting;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — FIREBALL SPELL (10M WEIGHTED ARC, IMPACT-ONLY DETONATION)
// ============================================================================
public final class FireballSpell {

    // #region 2. BALLISTIC ARC MATH & CAST ENTRYPOINT
    private static final double HAND_DROP_COMPENSATION = 1.25;
    private static final double HIT_INFLATION = 0.35;
    private static final int MAX_FLIGHT_LIFETIME_TICKS = 160;

    private FireballSpell() {}

    public static int computeCalibrationTicks(double rangeBlocks) {
        return Math.max(8, (int) Math.round(rangeBlocks * 1.6));
    }

    public static Vec3 computeInitialVelocity(Vec3 lookDir, double rangeBlocks, double gravity, int calibTicks) {
        Vec3 dir = lookDir.normalize();
        double forwardSpeed = rangeBlocks / (double) calibTicks;
        double arcLift = gravity > 0.0
                ? (0.5 * gravity * calibTicks) - (HAND_DROP_COMPENSATION / calibTicks)
                : 0.0;
        return new Vec3(
                dir.x * forwardSpeed,
                dir.y * forwardSpeed + arcLift,
                dir.z * forwardSpeed
        );
    }

    public static Vec3 evaluateArcPosition(Vec3 start, Vec3 initialVel, double gravity, double tick) {
        return new Vec3(
                start.x + initialVel.x * tick,
                start.y + initialVel.y * tick - 0.5 * gravity * tick * tick,
                start.z + initialVel.z * tick
        );
    }

    public static boolean cast(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 start = Targeting.handPoint(player);
        Vec3 look = player.getLookAngle().normalize();

        double range = SpellConfig.get(SpellConfig.FIREBALL_RANGE_BLOCKS) * PowerRules.range();
        double gravity = SpellConfig.get(SpellConfig.FIREBALL_GRAVITY);
        int calibTicks = computeCalibrationTicks(range);
        Vec3 initialVel = computeInitialVelocity(look, range, gravity, calibTicks);

        // 1. Clean Vanilla+ Hand Ignition Burst
        ParticleFx.ring(level, ParticleTypes.SMALL_FLAME, start, look, 0.22, 8, 0.01);
        ParticleFx.burst(level, ParticleTypes.FLAME, start, 6, 0.06, 0.025);
        ParticleFx.burst(level, ParticleTypes.SMOKE, start, 4, 0.05, 0.015);

        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.95F, 0.96F);
        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.65F, 1.12F);

        // 2. Broadcast 3D Voxel Flight Construct to Clients (flies until impact)
        SpellTargets.sendFx(level, start, new SpellFxPayload(
                SpellFxPayload.Kind.FIREBALL_FLIGHT,
                player.getId(),
                start,
                initialVel,
                (float) gravity,
                MAX_FLIGHT_LIFETIME_TICKS
        ));

        // 3. Server-Authoritative Parabolic Flight Loop (Explodes ONLY on Block/Entity Impact)
        boolean[] finished = {false};
        Effects.loop(MAX_FLIGHT_LIFETIME_TICKS, tick -> {
            if (finished[0] || !player.isAlive()) {
                return;
            }
            Vec3 prevPos = evaluateArcPosition(start, initialVel, gravity, tick);
            Vec3 nextPos = evaluateArcPosition(start, initialVel, gravity, tick + 1);

            if (nextPos.y < level.getMinBuildHeight() - 16) {
                finished[0] = true;
                SpellTargets.sendFx(level, prevPos, new SpellFxPayload(
                        SpellFxPayload.Kind.FIREBALL_DOUSE, player.getId(), prevPos, Vec3.ZERO, 0.0F, 1));
                return;
            }

            // Check Water Douse Along Segment
            if (SpellConfig.get(SpellConfig.FIREBALL_WATER_EXTINGUISH) == 1) {
                BlockPos checkPos = BlockPos.containing(nextPos);
                if (level.getFluidState(checkPos).is(FluidTags.WATER)) {
                    finished[0] = true;
                    douseInWater(level, player, nextPos);
                    return;
                }
            }

            // Trace Block & Entity Collision Along Curved Segment
            SpellTargets.SegmentHit hit = SpellTargets.traceSegment(level, player, prevPos, nextPos, HIT_INFLATION);

            // Clean Vanilla+ Flame, Ember & Smoke Trail Along the Arc
            Vec3 midPos = prevPos.lerp(hit.pos(), 0.5);
            ParticleFx.burst(level, ParticleTypes.FLAME, midPos, 2, 0.06, 0.01);
            ParticleFx.burst(level, ParticleTypes.SMALL_FLAME, hit.pos(), 3, 0.08, 0.012);
            ParticleFx.burst(level, ParticleTypes.SMOKE, prevPos, 2, 0.05, 0.008);
            if (tick % 2 == 0) {
                ParticleFx.burst(level, ParticleTypes.LAVA, midPos, 1, 0.04, 0.02);
            }

            // Explode ONLY when hitting an entity or a block
            if (hit.entity() != null || hit.hitBlock()) {
                finished[0] = true;
                detonate(level, player, hit.pos(), hit.entity());
            }
        });

        return true;
    }
    // #endregion

    // #region 3. WATER STEAM DOUSE & VANILLA+ EXPLOSION DETONATION
    private static void douseInWater(ServerLevel level, ServerPlayer caster, Vec3 pos) {
        SpellTargets.sendFx(level, pos, new SpellFxPayload(
                SpellFxPayload.Kind.FIREBALL_DOUSE,
                caster.getId(),
                pos,
                Vec3.ZERO,
                0.0F,
                1
        ));
        ParticleFx.burst(level, ParticleTypes.CLOUD, pos, 16, 0.35, 0.03);
        ParticleFx.burst(level, ParticleTypes.POOF, pos, 12, 0.28, 0.04);
        ParticleFx.burst(level, ParticleTypes.BUBBLE_POP, pos, 14, 0.35, 0.05);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.95F, 1.0F);
    }

    private static void detonate(ServerLevel level, ServerPlayer caster, Vec3 hitPos, LivingEntity directTarget) {
        double blastRadius = SpellConfig.get(SpellConfig.FIREBALL_BLAST_RADIUS) * PowerRules.radius();
        float directDamage = SpellTargets.scaleDamage(SpellConfig.get(SpellConfig.FIREBALL_DIRECT_DAMAGE));
        float splashDamage = SpellTargets.scaleDamage(SpellConfig.get(SpellConfig.FIREBALL_SPLASH_DAMAGE));
        float burnSeconds = (float) SpellConfig.get(SpellConfig.FIREBALL_BURN_SECONDS);

        // Broadcast 3D Voxel Blast Construct to Clients (stops FlightConstruct & spawns BlastConstruct)
        SpellTargets.sendFx(level, hitPos, new SpellFxPayload(
                SpellFxPayload.Kind.FIREBALL_BLAST,
                caster.getId(),
                hitPos,
                Vec3.ZERO,
                (float) blastRadius,
                18
        ));

        // Vanilla+ Explosion Particles
        ParticleFx.burst(level, ParticleTypes.EXPLOSION, hitPos, 1, 0.0, 0.0);
        ParticleFx.ring(level, ParticleTypes.FLAME, hitPos.add(0.0, 0.10, 0.0), Vectors.UP, blastRadius * 0.85, 24, 0.02);
        ParticleFx.burst(level, ParticleTypes.FLAME, hitPos, 24, blastRadius * 0.32, 0.06);
        ParticleFx.burst(level, ParticleTypes.SMALL_FLAME, hitPos, 18, blastRadius * 0.40, 0.05);
        ParticleFx.burst(level, ParticleTypes.LAVA, hitPos, 10, 0.25, 0.08);
        ParticleFx.burst(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, hitPos.add(0.0, 0.25, 0.0), 8, 0.35, 0.015);
        ParticleFx.burst(level, ParticleTypes.LARGE_SMOKE, hitPos, 10, 0.35, 0.03);

        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.95F, 1.12F);

        DamageSource source = caster.damageSources().playerAttack(caster);

        // Direct Target Damage
        if (directTarget != null && SpellTargets.canHurt(caster, directTarget)) {
            directTarget.invulnerableTime = 0;
            boolean hurt = directTarget.hurt(source, directDamage + splashDamage);
            if (burnSeconds > 0.0F && directTarget.isAlive()) {
                directTarget.igniteForSeconds(burnSeconds);
            }
            if (hurt && !directTarget.isAlive()) {
                triggerAshDisintegration(level, caster, directTarget);
            }
        }

        // AoE Splash Damage with Distance Falloff
        AABB blastBox = AABB.ofSize(hitPos, blastRadius * 2.0, blastRadius * 2.0, blastRadius * 2.0);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, blastBox,
                e -> e != directTarget && SpellTargets.canHurt(caster, e));

        for (LivingEntity victim : victims) {
            double dist = victim.position().distanceTo(hitPos);
            if (dist > blastRadius) {
                continue;
            }
            double falloff = Math.max(0.25, 1.0 - (dist / blastRadius) * 0.65);
            float dmg = (float) (splashDamage * falloff);
            victim.invulnerableTime = 0;
            boolean hurt = victim.hurt(source, dmg);
            if (burnSeconds > 0.0F && victim.isAlive()) {
                victim.igniteForSeconds((float) (burnSeconds * falloff));
            }
            Vec3 knock = victim.position().subtract(hitPos);
            if (knock.lengthSqr() > 1.0E-4) {
                Vec3 push = knock.normalize().scale(0.45 * falloff * PowerRules.knockback());
                victim.push(push.x, 0.22 * falloff, push.z);
            }
            if (hurt && !victim.isAlive()) {
                triggerAshDisintegration(level, caster, victim);
            }
        }

        // Optional 3x3 Block Ignition
        if (SpellConfig.get(SpellConfig.FIREBALL_IGNITE_BLOCKS) == 1) {
            igniteGround(level, BlockPos.containing(hitPos));
        }
    }

    private static void triggerAshDisintegration(ServerLevel level, ServerPlayer caster, LivingEntity victim) {
        Vec3 center = Targeting.chestPoint(victim);
        ParticleFx.burst(level, ParticleTypes.ASH, center, 24, 0.30, 0.05);
        ParticleFx.burst(level, ParticleTypes.SMALL_FLAME, center, 10, 0.25, 0.03);
        SpellTargets.sendFx(level, center, new SpellFxPayload(
                SpellFxPayload.Kind.FIREBALL_ASH,
                caster.getId(),
                victim.position(),
                new Vec3(victim.getBbWidth(), victim.getBbHeight(), 0.0),
                0.8F,
                22
        ));
    }

    private static void igniteGround(ServerLevel level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 && dz != 0 && level.random.nextFloat() > 0.45F) {
                    continue;
                }
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.isEmptyBlock(pos)) {
                        BlockState fireState = BaseFireBlock.getState(level, pos);
                        if (fireState.canSurvive(level, pos)) {
                            level.setBlockAndUpdate(pos, fireState);
                            break;
                        }
                    }
                }
            }
        }
    }
    // #endregion
}
