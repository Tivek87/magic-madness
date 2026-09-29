package com.magicmadness.spell.electric;

// #region 1. IMPORTS
import com.magicmadness.config.PowerRules;
import com.magicmadness.config.SpellConfig;
import com.magicmadness.engine.fx.ParticleFx;
import com.magicmadness.engine.tick.Effects;
import com.magicmadness.network.SpellFxPayload;
import com.magicmadness.spell.SpellTargets;
import com.magicmadness.spell.Targeting;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — CUSTOM LIGHTNING STRIKE SPELL (SKY BOLT, SHOCKWAVE & CHAIN)
// ============================================================================
public final class LightningSpell {

    // #region 2. CONSTANTS & PRE-ALLOCATED ELECTRIC PARTICLES
    private static final double LOCK_INFLATION = 0.65;
    private static final double SKY_BOLT_HEIGHT = 10.5;
    private static final DustParticleOptions DUST_STORM_CLOUD = ParticleFx.dust(0x1B283B, 2.40F);
    private static final Vec3 UP_NORMAL = new Vec3(0.0, 1.0, 0.0);

    private static final DustParticleOptions DUST_WHITE_CORE = ParticleFx.dust(0xF1FCFD, 1.15F);
    private static final DustParticleOptions DUST_ELECTRIC_CYAN = ParticleFx.dust(0x73DAFC, 1.10F);
    private static final DustColorTransitionOptions FADE_BOLT = ParticleFx.fade(0xF1FCFD, 0x235698, 1.25F);
    private static final DustColorTransitionOptions FADE_SHOCK = ParticleFx.fade(0x73DAFC, 0x111D33, 1.10F);

    private LightningSpell() {}
    // #endregion

    // #region 3. CAST ENTRYPOINT, SKY-RUNE LOCK, BOLT STRIKE & CHAIN LIGHTNING
    public static boolean cast(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        double range = Math.max(4.0, SpellConfig.get(SpellConfig.LIGHTNING_RANGE_BLOCKS) * PowerRules.range());
        int chargeTicks = Math.max(0, SpellConfig.get(SpellConfig.LIGHTNING_CHARGE_TICKS));

        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 maxEnd = eye.add(look.scale(range));

        SpellTargets.SegmentHit hit = SpellTargets.traceSegment(level, caster, eye, maxEnd, LOCK_INFLATION);
        LivingEntity lockedTarget = hit.entity();
        Vec3[] strikeSpot = new Vec3[]{
                lockedTarget != null ? lockedTarget.position() : hit.pos()
        };

        // Hand cast spark & sound
        Vec3 hand = Targeting.handPoint(caster);
        ParticleFx.burst(level, ParticleTypes.ELECTRIC_SPARK, hand, 14, 0.15, 0.18);
        ParticleFx.ring(level, DUST_ELECTRIC_CYAN, hand, look, 0.42, 12, 0.01);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.85F, 1.65F);

        if (chargeTicks > 0) {
            SpellTargets.sendFx(level, strikeSpot[0], new SpellFxPayload(
                    SpellFxPayload.Kind.LIGHTNING_CHARGE,
                    caster.getId(),
                    strikeSpot[0],
                    strikeSpot[0].add(0.0, SKY_BOLT_HEIGHT, 0.0),
                    (float) (SpellConfig.get(SpellConfig.LIGHTNING_SHOCK_RADIUS) * PowerRules.radius()),
                    chargeTicks + 2
            ));
            level.playSound(null, strikeSpot[0].x, strikeSpot[0].y, strikeSpot[0].z,
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.8F);
        }

        boolean raining = level.isRainingAt(BlockPos.containing(strikeSpot[0])) || level.isThundering();
        int maxChains = raining
                ? SpellConfig.get(SpellConfig.LIGHTNING_MAX_CHAINS_RAIN)
                : SpellConfig.get(SpellConfig.LIGHTNING_MAX_CHAINS_CLEAR);
        double chainRange = Math.max(1.0, SpellConfig.get(SpellConfig.LIGHTNING_CHAIN_RANGE) * PowerRules.range());

        Set<UUID> struckTargets = new HashSet<>();
        Vec3[] lastChainOrigin = new Vec3[]{null};
        int[] chainsDone = new int[]{0};

        Effects.start(level, (lvl, age) -> {
            // Phase 1: Sky-Rune Lock-On Charge
            if (age < chargeTicks) {
                if (lockedTarget != null && lockedTarget.isAlive()) {
                    strikeSpot[0] = lockedTarget.position();
                }
                double prog = (age + 1.0) / Math.max(1.0, chargeTicks);
                double runeRadius = 1.6 * (1.15 - 0.45 * prog);
                Vec3 ground = strikeSpot[0].add(0.0, 0.08, 0.0);
                Vec3 cloudCenter = strikeSpot[0].add(0.0, SKY_BOLT_HEIGHT, 0.0);
                ParticleFx.ring(lvl, DUST_ELECTRIC_CYAN, ground, UP_NORMAL, runeRadius, 16, 0.01);
                ParticleFx.star(lvl, DUST_WHITE_CORE, ground, UP_NORMAL, 4, runeRadius * 0.85, 3, 0.01);
                ParticleFx.send(lvl, ParticleTypes.ELECTRIC_SPARK, ground.add(0.0, 0.25, 0.0),
                        6, 0.45, 0.35, 0.45, 0.08);
                // Overhead Storm Cloud Particles During Charge
                ParticleFx.disc(lvl, DUST_STORM_CLOUD, cloudCenter, UP_NORMAL, 2.4 * prog, 18, 0.01);
                ParticleFx.send(lvl, ParticleTypes.CLOUD, cloudCenter, 10, 1.35, 0.35, 1.35, 0.01);
                ParticleFx.send(lvl, ParticleTypes.LARGE_SMOKE, cloudCenter, 8, 1.25, 0.30, 1.25, 0.01);
                ParticleFx.send(lvl, ParticleTypes.ELECTRIC_SPARK, cloudCenter.subtract(0.0, 0.35, 0.0),
                        8, 1.1, 0.25, 1.1, 0.12);
                if (age % 2 == 0) {
                    lvl.playSound(null, strikeSpot[0].x, strikeSpot[0].y, strikeSpot[0].z,
                            SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
                            0.45F, 1.45F + (float) prog * 0.55F);
                }
                return true;
            }

            // Phase 2: Primary Custom Sky-to-Ground Lightning Bolt Strike
            if (age == chargeTicks) {
                if (lockedTarget != null && lockedTarget.isAlive()) {
                    strikeSpot[0] = lockedTarget.position();
                }
                Vec3 spot = strikeSpot[0];
                LivingEntity primary = executePrimaryStrike(lvl, caster, spot, lockedTarget, struckTargets);
                lastChainOrigin[0] = primary != null ? Targeting.chestPoint(primary) : spot.add(0.0, 0.9, 0.0);
                return maxChains > 0;
            }

            // Phase 3: Chain Lightning Arcs Every 2 Ticks
            int postStrikeTicks = age - chargeTicks;
            if (postStrikeTicks % 2 == 0) {
                if (chainsDone[0] >= maxChains || lastChainOrigin[0] == null) {
                    return false;
                }
                LivingEntity next = findNearestChainTarget(lvl, caster, lastChainOrigin[0], chainRange, struckTargets);
                if (next == null) {
                    return false;
                }
                struckTargets.add(next.getUUID());
                chainsDone[0]++;
                Vec3 fromChest = lastChainOrigin[0];
                Vec3 toChest = Targeting.chestPoint(next);
                executeChainJump(lvl, caster, fromChest, toChest, next, chainsDone[0]);
                lastChainOrigin[0] = toChest;
                return chainsDone[0] < maxChains;
            }
            return chainsDone[0] < maxChains;
        });

        return true;
    }
    // #endregion

    // #region 4. PRIMARY BOLT IMPACT, SHOCKWAVE & CHAIN JUMP EXECUTION
    private static LivingEntity executePrimaryStrike(ServerLevel level, ServerPlayer caster, Vec3 spot,
                                                     LivingEntity lockedTarget, Set<UUID> struckTargets) {
        double shockRadius = Math.max(0.5, SpellConfig.get(SpellConfig.LIGHTNING_SHOCK_RADIUS) * PowerRules.radius());
        float strikeDamage = SpellTargets.scaleDamage(SpellConfig.get(SpellConfig.LIGHTNING_STRIKE_DAMAGE));
        float shockDamage = SpellTargets.scaleDamage(SpellConfig.get(SpellConfig.LIGHTNING_SHOCK_DAMAGE));
        double kbScale = PowerRules.knockback();

        // Overhead Storm Cloud Center directly above the strike spot
        double skyOffX = (level.random.nextDouble() - 0.5) * 0.9;
        double skyOffZ = (level.random.nextDouble() - 0.5) * 0.9;
        Vec3 skyStart = spot.add(skyOffX, SKY_BOLT_HEIGHT, skyOffZ);

        // 1. Broadcast Custom 3D Storm Cloud, Multi-Branched Lightning Bolt & Shockwave VFX
        SpellTargets.sendFx(level, spot, new SpellFxPayload(
                SpellFxPayload.Kind.LIGHTNING_BOLT,
                caster.getId(),
                spot,
                skyStart,
                (float) shockRadius,
                28
        ));

        // 2. Server-Side Storm Cloud & Supporting Geometric Particles
        ParticleFx.disc(level, DUST_STORM_CLOUD, skyStart, UP_NORMAL, 2.8, 28, 0.02);
        ParticleFx.send(level, ParticleTypes.CLOUD, skyStart, 26, 1.6, 0.45, 1.6, 0.02);
        ParticleFx.send(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, skyStart, 18, 1.5, 0.35, 1.5, 0.01);
        ParticleFx.send(level, ParticleTypes.LARGE_SMOKE, skyStart, 16, 1.4, 0.35, 1.4, 0.02);
        ParticleFx.send(level, ParticleTypes.ELECTRIC_SPARK, skyStart.subtract(0.0, 0.4, 0.0), 28, 1.4, 0.35, 1.4, 0.25);
        ParticleFx.send(level, ParticleTypes.FLASH, spot.add(0.0, 0.6, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        ParticleFx.sphere(level, FADE_BOLT, spot.add(0.0, 0.5, 0.0), Math.min(2.2, shockRadius * 0.7), 28, 0.04);
        ParticleFx.ring(level, DUST_WHITE_CORE, spot.add(0.0, 0.12, 0.0), UP_NORMAL, shockRadius * 0.65, 22, 0.02);
        ParticleFx.ring(level, FADE_SHOCK, spot.add(0.0, 0.10, 0.0), UP_NORMAL, shockRadius, 28, 0.02);
        ParticleFx.send(level, ParticleTypes.ELECTRIC_SPARK, spot.add(0.0, 0.7, 0.0), 42, 0.65, 0.85, 0.65, 0.45);

        level.playSound(null, spot.x, spot.y, spot.z,
                SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.55F, 1.05F);
        level.playSound(null, spot.x, spot.y, spot.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.15F, 1.25F);
        level.playSound(null, spot.x, spot.y, spot.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.75F, 1.55F);

        DamageSource dmgSource = caster.damageSources().indirectMagic(caster, caster);

        // 3. Find Primary Target (Locked Target or Closest Within 1.8m of Bolt Core)
        LivingEntity primary = (lockedTarget != null && lockedTarget.isAlive()
                && lockedTarget.position().distanceToSqr(spot) <= 9.0 && SpellTargets.canHurt(caster, lockedTarget))
                ? lockedTarget
                : null;

        AABB box = new AABB(
                spot.x - shockRadius, spot.y - 1.5, spot.z - shockRadius,
                spot.x + shockRadius, spot.y + shockRadius + 2.5, spot.z + shockRadius
        );
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> SpellTargets.canHurt(caster, e));

        if (primary == null) {
            double bestDistSq = 2.0 * 2.0;
            for (LivingEntity v : victims) {
                double dSq = v.position().distanceToSqr(spot);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    primary = v;
                }
            }
        }

        // 4. Direct Strike Damage on Primary Target
        if (primary != null) {
            struckTargets.add(primary.getUUID());
            if (strikeDamage > 0.0F) {
                primary.invulnerableTime = 0;
                primary.hurt(dmgSource, strikeDamage);
            }
        }

        // 5. Electric Shockwave AoE Damage, Slowness III Stun & Upward Electric Jolt
        for (LivingEntity victim : victims) {
            double dist = Math.sqrt(victim.position().distanceToSqr(spot));
            if (dist > shockRadius + 0.65) {
                continue;
            }
            struckTargets.add(victim.getUUID());
            double falloff = (victim == primary) ? 1.0 : Math.max(0.40, 1.0 - (dist / (shockRadius + 0.65)) * 0.60);
            float dmg = (float) (shockDamage * falloff);
            if (dmg > 0.0F) {
                victim.invulnerableTime = 0;
                victim.hurt(dmgSource, dmg);
            }
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, true, true));

            if (kbScale > 0.0) {
                double basePush = SpellConfig.get(SpellConfig.LIGHTNING_KNOCKBACK_STRENGTH);
                double baseLift = SpellConfig.get(SpellConfig.LIGHTNING_KNOCKBACK_LIFT);
                Vec3 away = victim.position().subtract(spot);
                Vec3 horiz = new Vec3(away.x, 0.0, away.z);
                Vec3 casterLookHoriz = new Vec3(caster.getLookAngle().x, 0.0, caster.getLookAngle().z);
                Vec3 fallbackDir = casterLookHoriz.lengthSqr() > 1.0E-4 ? casterLookHoriz.normalize() : new Vec3(1.0, 0.0, 0.0);
                Vec3 pushDir = horiz.lengthSqr() > 0.04 ? horiz.normalize() : fallbackDir;
                double push = basePush * falloff * kbScale;
                double lift = baseLift * (victim == primary ? 1.12 : falloff) * kbScale;
                Vec3 curVel = victim.getDeltaMovement();
                victim.setDeltaMovement(curVel.x * 0.25 + pushDir.x * push, Math.max(curVel.y * 0.25, 0.0) + lift, curVel.z * 0.25 + pushDir.z * push);
                victim.hasImpulse = true;
                victim.hurtMarked = true;
            }

            if (!victim.isAlive()) {
                SpellTargets.sendFx(level, victim.position(), new SpellFxPayload(
                        SpellFxPayload.Kind.FIREBALL_ASH,
                        caster.getId(),
                        victim.position(),
                        new Vec3(victim.getBbWidth(), victim.getBbHeight(), 0.0),
                        victim.getBbWidth(),
                        26
                ));
            }
        }

        return primary;
    }

    private static LivingEntity findNearestChainTarget(ServerLevel level, ServerPlayer caster, Vec3 origin,
                                                       double chainRange, Set<UUID> struckTargets) {
        AABB searchBox = new AABB(origin, origin).inflate(chainRange);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> SpellTargets.canHurt(caster, e) && !struckTargets.contains(e.getUUID()));
        LivingEntity best = null;
        double bestDistSq = chainRange * chainRange;
        for (LivingEntity c : candidates) {
            double dSq = Targeting.chestPoint(c).distanceToSqr(origin);
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                best = c;
            }
        }
        return best;
    }

    private static void executeChainJump(ServerLevel level, ServerPlayer caster, Vec3 fromChest, Vec3 toChest,
                                         LivingEntity target, int jumpIndex) {
        float baseChainDmg = SpellTargets.scaleDamage(SpellConfig.get(SpellConfig.LIGHTNING_CHAIN_DAMAGE));
        float decay = (float) Math.pow(0.88, Math.max(0, jumpIndex - 1));
        float finalDmg = baseChainDmg * decay;

        SpellTargets.sendFx(level, toChest, new SpellFxPayload(
                SpellFxPayload.Kind.LIGHTNING_ARC,
                caster.getId(),
                fromChest,
                toChest,
                0.28F,
                10
        ));

        ParticleFx.send(level, ParticleTypes.ELECTRIC_SPARK, toChest, 18, 0.28, 0.32, 0.28, 0.24);
        level.playSound(null, toChest.x, toChest.y, toChest.z,
                SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
                0.85F, 1.55F + jumpIndex * 0.10F);

        if (finalDmg > 0.0F) {
            target.invulnerableTime = 0;
            target.hurt(caster.damageSources().indirectMagic(caster, caster), finalDmg);
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, true, true));

        double kbScale = PowerRules.knockback();
        if (kbScale > 0.0) {
            double basePush = SpellConfig.get(SpellConfig.LIGHTNING_KNOCKBACK_STRENGTH) * 0.58;
            double baseLift = SpellConfig.get(SpellConfig.LIGHTNING_KNOCKBACK_LIFT) * 0.58;
            Vec3 dir = toChest.subtract(fromChest);
            Vec3 horiz = new Vec3(dir.x, 0.0, dir.z);
            Vec3 pushDir = horiz.lengthSqr() > 1.0E-4 ? horiz.normalize() : new Vec3(caster.getLookAngle().x, 0.0, caster.getLookAngle().z).normalize();
            Vec3 curVel = target.getDeltaMovement();
            target.setDeltaMovement(curVel.x * 0.3 + pushDir.x * basePush * kbScale, Math.max(curVel.y * 0.3, 0.0) + baseLift * kbScale, curVel.z * 0.3 + pushDir.z * basePush * kbScale);
            target.hasImpulse = true;
            target.hurtMarked = true;
        }

        if (!target.isAlive()) {
            SpellTargets.sendFx(level, target.position(), new SpellFxPayload(
                    SpellFxPayload.Kind.FIREBALL_ASH,
                    caster.getId(),
                    target.position(),
                    new Vec3(target.getBbWidth(), target.getBbHeight(), 0.0),
                    target.getBbWidth(),
                    26
            ));
        }
    }
    // #endregion
}
