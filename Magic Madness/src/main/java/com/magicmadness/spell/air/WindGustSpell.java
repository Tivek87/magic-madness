package com.magicmadness.spell.air;

// #region 1. IMPORTS
import com.magicmadness.config.PowerRules;
import com.magicmadness.config.SpellConfig;
import com.magicmadness.engine.fx.ParticleFx;
import com.magicmadness.engine.tick.Effects;
import com.magicmadness.network.SpellFxPayload;
import com.magicmadness.spell.SpellTargets;
import com.magicmadness.spell.Targeting;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — CUSTOM WIND GUST SPELL (LOW DAMAGE, MASSIVE KNOCKBACK & VORTEX)
// ============================================================================
public final class WindGustSpell {

    // #region 2. PRE-ALLOCATED AIR / STORM-MINT PARTICLES
    private static final DustParticleOptions DUST_BREEZE_WHITE = ParticleFx.dust(0xF1FCFB, 1.15F);
    private static final DustParticleOptions DUST_STORM_MINT = ParticleFx.dust(0xA1F3E3, 1.25F);
    private static final DustColorTransitionOptions FADE_SLIPSTREAM = ParticleFx.fade(0xF1FCFB, 0x2D9391, 1.20F);

    private WindGustSpell() {}
    // #endregion

    // #region 3. CAST ENTRYPOINT, STORM CONE KNOCKBACK, PROJECTILE REFLECT & AIR JUMP
    public static boolean cast(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        double range = Math.max(3.0, SpellConfig.get(SpellConfig.WIND_GUST_RANGE_BLOCKS) * PowerRules.range());
        double halfAngleDeg = Math.max(12.0, SpellConfig.get(SpellConfig.WIND_GUST_CONE_DEGREES));
        double cosThreshold = Math.cos(Math.toRadians(halfAngleDeg));

        float gustDamage = SpellTargets.scaleDamage(SpellConfig.get(SpellConfig.WIND_GUST_DAMAGE));
        double kbScale = PowerRules.knockback();
        double basePush = SpellConfig.get(SpellConfig.WIND_GUST_PUSH_STRENGTH) * kbScale;
        double baseLift = SpellConfig.get(SpellConfig.WIND_GUST_LIFT_STRENGTH) * kbScale;
        double recoilBoost = SpellConfig.get(SpellConfig.WIND_GUST_RECOIL_BOOST) * kbScale;
        boolean reflectProjectiles = SpellConfig.get(SpellConfig.WIND_GUST_REFLECT_PROJECTILES) == 1;

        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 hand = Targeting.handPoint(caster);
        Vec3 coneEnd = hand.add(look.scale(range));

        // 1. Broadcast Custom 3D Wind Gust Vortex & Crescent Blades VFX
        SpellTargets.sendFx(level, hand, new SpellFxPayload(
                SpellFxPayload.Kind.WIND_GUST,
                caster.getId(),
                hand,
                coneEnd,
                (float) range,
                16
        ));

        // 2. Layered Wind Burst Audio
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.35F, 0.92F);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.15F, 1.18F);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.85F, 1.45F);

        // 3. Server-Side Mathematical Slipstream Helix & Expanding Vortex Rings
        ParticleFx.helix(level, FADE_SLIPSTREAM, hand, look, range * 0.92, 1.05, 2.2, 2, 18, 0.0, 0.02);
        ParticleFx.ring(level, DUST_BREEZE_WHITE, hand.add(look.scale(1.2)), look, 0.85, 16, 0.02);
        ParticleFx.send(level, ParticleTypes.CLOUD, hand.add(look.scale(2.2)), 22, 0.65, 0.55, 0.65, 0.18);
        ParticleFx.send(level, ParticleTypes.SWEEP_ATTACK, hand.add(look.scale(1.8)), 3, 0.45, 0.35, 0.45, 0.0);

        Effects.start(level, (lvl, age) -> {
            if (age >= 5) {
                return false;
            }
            double stepDist = 1.6 + age * (range * 0.17);
            double stepRadius = 0.75 + age * 0.48;
            Vec3 ringCenter = hand.add(look.scale(stepDist));
            ParticleFx.ring(lvl, (age % 2 == 0) ? DUST_STORM_MINT : DUST_BREEZE_WHITE,
                    ringCenter, look, stepRadius, 18, 0.02);
            ParticleFx.send(lvl, ParticleTypes.CLOUD, ringCenter, 8,
                    stepRadius * 0.45, stepRadius * 0.45, stepRadius * 0.45, 0.08);
            return true;
        });

        // 4. Extinguish Caster if Burning & Extinguish Fire Blocks in Front Cone
        if (caster.isOnFire()) {
            caster.clearFire();
        }
        extinguishFireInCone(level, eye, look, Math.min(range, 7.0));

        // 5. Self Air-Jump / Fall-Cushion Recoil When Airborne or Aiming Downward
        if (!caster.onGround() || look.y < -0.32) {
            caster.resetFallDistance();
            if (recoilBoost > 0.0) {
                Vec3 recoil = look.scale(-recoilBoost);
                Vec3 cur = caster.getDeltaMovement();
                double newY = look.y < -0.25
                        ? Math.max(cur.y * 0.35 + recoil.y * 1.15, recoilBoost * 0.82)
                        : cur.y * 0.65 + recoil.y * 0.65;
                caster.setDeltaMovement(
                        cur.x * 0.55 + recoil.x * 0.85,
                        newY,
                        cur.z * 0.55 + recoil.z * 0.85
                );
                caster.hasImpulse = true;
                caster.hurtMarked = true;
            }
        }

        // 6. Reflect / Deflect Incoming Projectiles Inside the Storm Cone
        AABB coneBox = new AABB(eye, eye.add(look.scale(range))).inflate(range * 0.65 + 1.5);
        if (reflectProjectiles) {
            List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, coneBox,
                    p -> p.isAlive() && p.getOwner() != caster);
            for (Projectile proj : projectiles) {
                Vec3 toProj = proj.position().add(0.0, proj.getBbHeight() * 0.5, 0.0).subtract(eye);
                double dist = toProj.length();
                if (dist > range + 1.2) {
                    continue;
                }
                double dot = dist < 1.2 ? 1.0 : toProj.normalize().dot(look);
                if (dot < cosThreshold && dist > 1.8) {
                    continue;
                }
                double speed = Math.max(1.85, proj.getDeltaMovement().length() * 1.75);
                Vec3 deflectedVel = look.scale(speed);
                proj.setDeltaMovement(deflectedVel);
                proj.setOwner(caster);
                proj.hasImpulse = true;
                proj.hurtMarked = true;
                ParticleFx.burst(level, ParticleTypes.CLOUD, proj.position(), 10, 0.20, 0.12);
                ParticleFx.ring(level, DUST_BREEZE_WHITE, proj.position(), look, 0.55, 12, 0.01);
            }
        }

        // 7. Low Damage & Massive Gale Knockback on Enemies Inside the Cone
        DamageSource dmgSource = caster.damageSources().indirectMagic(caster, caster);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, coneBox,
                e -> SpellTargets.canHurt(caster, e));

        Vec3 horizLook = new Vec3(look.x, 0.0, look.z);
        Vec3 fallbackHoriz = horizLook.lengthSqr() > 1.0E-4 ? horizLook.normalize() : new Vec3(1.0, 0.0, 0.0);

        for (LivingEntity victim : targets) {
            Vec3 chest = Targeting.chestPoint(victim);
            Vec3 toVictim = chest.subtract(eye);
            double dist = toVictim.length();
            if (dist > range + 0.85) {
                continue;
            }
            double dot = dist < 1.35 ? 1.0 : toVictim.normalize().dot(look);
            if (dot < cosThreshold && dist > 1.6) {
                continue;
            }

            // Distance falloff keeps close-to-mid gusts devastating while full 10m cone still launches hard
            double falloff = Math.max(0.52, 1.0 - (dist / (range + 1.5)) * 0.48);
            float finalDmg = (float) (gustDamage * falloff);
            if (finalDmg > 0.0F) {
                victim.invulnerableTime = 0;
                victim.hurt(dmgSource, finalDmg);
            }

            if (kbScale > 0.0) {
                Vec3 awayHoriz = new Vec3(toVictim.x, 0.0, toVictim.z);
                Vec3 radialDir = awayHoriz.lengthSqr() > 0.04 ? awayHoriz.normalize() : fallbackHoriz;
                // Blend 70% caster aim direction + 30% radial cone direction so enemies fly where you aim
                Vec3 launchDir = fallbackHoriz.scale(0.70).add(radialDir.scale(0.30)).normalize();

                double resist = Math.max(0.15, 1.0 - victim.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) * 0.55);
                double push = basePush * falloff * resist;
                double lift = (baseLift + Math.max(0.0, look.y * 0.38 * kbScale)) * falloff * resist;

                Vec3 curVel = victim.getDeltaMovement();
                victim.setDeltaMovement(
                        curVel.x * 0.15 + launchDir.x * push,
                        Math.max(curVel.y * 0.15, 0.0) + lift,
                        curVel.z * 0.15 + launchDir.z * push
                );
                victim.hasImpulse = true;
                victim.hurtMarked = true;
                victim.fallDistance = 0.0F;
            }

            ParticleFx.send(level, ParticleTypes.CLOUD, chest, 12, 0.35, 0.35, 0.35, 0.16);
            ParticleFx.ring(level, DUST_STORM_MINT, chest, look, 0.68, 12, 0.02);

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

        return true;
    }

    private static void extinguishFireInCone(ServerLevel level, Vec3 eye, Vec3 look, double maxDist) {
        for (double d = 1.0; d <= maxDist; d += 1.25) {
            Vec3 center = eye.add(look.scale(d));
            BlockPos basePos = BlockPos.containing(center);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos pos = basePos.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() instanceof BaseFireBlock) {
                            level.removeBlock(pos, false);
                            ParticleFx.send(level, ParticleTypes.CLOUD,
                                    Vec3.atCenterOf(pos), 4, 0.2, 0.2, 0.2, 0.04);
                        }
                    }
                }
            }
        }
    }
    // #endregion
}
