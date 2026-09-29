package com.magicmadness.spell;

// #region 1. IMPORTS
import com.magicmadness.engine.math.Vectors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — SPELL TARGETING & CAST ORIGIN HELPERS
// ============================================================================
public final class Targeting {

    // #region 2. HAND & CHEST POINTS
    private Targeting() {}

    public static Vec3 handPoint(Entity caster) {
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 right = Vectors.perpendicular(look);
        return new Vec3(caster.getX(), caster.getEyeY() - 0.28, caster.getZ())
                .add(look.scale(0.55))
                .add(right.scale(0.28));
    }

    public static Vec3 chestPoint(Entity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.55, 0.0);
    }
    // #endregion
}
