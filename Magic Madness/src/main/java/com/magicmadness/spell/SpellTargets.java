package com.magicmadness.spell;

// #region 1. IMPORTS
import com.magicmadness.config.PowerRules;
import com.magicmadness.network.SpellFxPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
// #endregion

// ============================================================================
// MAGIC MADNESS — SERVER TARGET FILTERING, RAYCASTING & FX BROADCAST
// ============================================================================
public final class SpellTargets {

    // #region 2. TARGET FILTERING & SEGMENT RAYCASTS
    private SpellTargets() {}

    public static boolean canHurt(ServerPlayer caster, LivingEntity target) {
        if (target == null || !target.isAlive() || target == caster || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player otherPlayer) {
            if (!caster.canHarmPlayer(otherPlayer)) {
                return false;
            }
            if (!PowerRules.friendlyFire() && caster.getTeam() != null && caster.isAlliedTo(otherPlayer)) {
                return false;
            }
        }
        if (!PowerRules.friendlyFire() && target instanceof OwnableEntity ownable
                && caster.getUUID().equals(ownable.getOwnerUUID())) {
            return false;
        }
        return true;
    }

    public static float scaleDamage(double rawDamage) {
        return (float) (rawDamage * PowerRules.damage());
    }

    public static int scaleCooldown(int rawCooldownTicks) {
        return Math.max(0, (int) Math.round(rawCooldownTicks * PowerRules.cooldowns()));
    }

    public record SegmentHit(Vec3 pos, LivingEntity entity, boolean hitBlock) {}

    public static SegmentHit traceSegment(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 to, double hitInflation) {
        BlockHitResult blockHit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 endPos = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : to;
        boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;

        AABB sweepBox = new AABB(from, endPos).inflate(hitInflation + 0.5);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> canHurt(caster, e));

        LivingEntity closestEntity = null;
        Vec3 closestHitPos = endPos;
        double closestDistSq = from.distanceToSqr(endPos);

        for (LivingEntity candidate : candidates) {
            AABB entityBox = candidate.getBoundingBox().inflate(hitInflation);
            if (entityBox.contains(from)) {
                return new SegmentHit(from, candidate, false);
            }
            Optional<Vec3> clip = entityBox.clip(from, endPos);
            if (clip.isPresent()) {
                double distSq = from.distanceToSqr(clip.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closestEntity = candidate;
                    closestHitPos = clip.get();
                }
            }
        }

        if (closestEntity != null) {
            return new SegmentHit(closestHitPos, closestEntity, false);
        }
        return new SegmentHit(endPos, null, hitBlock);
    }

    public static void sendFx(ServerLevel level, Vec3 origin, SpellFxPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, origin.x, origin.y, origin.z, 96.0, payload);
    }
    // #endregion
}
