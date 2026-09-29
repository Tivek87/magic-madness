package com.magicmadness.engine.client.fx;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import com.magicmadness.config.client.ClientSettings;
import com.magicmadness.engine.math.Noise;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
// #endregion

// ============================================================================
// MAGIC MADNESS — MULTI-INSTANCE PERLIN TRAUMA CAMERA SHAKE
// ============================================================================
@EventBusSubscriber(modid = MagicMadness.MODID, value = Dist.CLIENT)
public final class CameraShake {

    // #region 2. SHAKE INSTANCE & STORAGE
    private static final int MAX_SHAKES = 8;
    private static final List<ShakeInstance> SHAKES = new ArrayList<>();

    private static final class ShakeInstance {
        final float trauma;
        final int durationTicks;
        final float frequency;
        final double seed;
        int ageTicks;

        ShakeInstance(float trauma, int durationTicks, float frequency, double seed) {
            this.trauma = Mth.clamp(trauma, 0.0F, 1.5F);
            this.durationTicks = Math.max(1, durationTicks);
            this.frequency = frequency;
            this.seed = seed;
        }
    }

    private CameraShake() {}

    public static void addAt(Vec3 sourcePos, double maxDistance, float trauma, int durationTicks, float frequency) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        double dist = player.position().distanceTo(sourcePos);
        if (dist >= maxDistance) {
            return;
        }
        float falloff = (float) (1.0 - (dist / maxDistance));
        add(trauma * falloff * falloff, durationTicks, frequency);
    }

    public static void add(float trauma, int durationTicks, float frequency) {
        double scale = ClientSettings.get(ClientSettings.CAMERA_SHAKE);
        if (scale <= 0.001 || trauma <= 0.001F) {
            return;
        }
        if (SHAKES.size() >= MAX_SHAKES) {
            SHAKES.removeFirst();
        }
        double seed = (System.nanoTime() & 0xFFFF) * 0.137;
        SHAKES.add(new ShakeInstance((float) (trauma * scale), durationTicks, frequency, seed));
    }
    // #endregion

    // #region 3. TICK & CAMERA ANGLE COMPUTATION
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        Iterator<ShakeInstance> it = SHAKES.iterator();
        while (it.hasNext()) {
            ShakeInstance s = it.next();
            s.ageTicks++;
            if (s.ageTicks >= s.durationTicks) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (SHAKES.isEmpty() || Minecraft.getInstance().isPaused()) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float totalPitch = 0.0F;
        float totalYaw = 0.0F;
        float totalRoll = 0.0F;

        for (ShakeInstance s : SHAKES) {
            float t = (s.ageTicks + partial) / (float) s.durationTicks;
            if (t >= 1.0F) {
                continue;
            }
            float decay = (1.0F - t) * (1.0F - t);
            float effectiveTrauma = s.trauma * decay;
            float amp = effectiveTrauma * effectiveTrauma;
            double time = (s.ageTicks + partial) * s.frequency;

            totalPitch += (float) Noise.signed(time, s.seed, 1.1) * amp * 4.2F;
            totalYaw += (float) Noise.signed(time, s.seed + 17.3, 2.3) * amp * 4.2F;
            totalRoll += (float) Noise.signed(time, s.seed + 41.7, 3.7) * amp * 2.8F;
        }

        event.setPitch(event.getPitch() + totalPitch);
        event.setYaw(event.getYaw() + totalYaw);
        event.setRoll(event.getRoll() + totalRoll);
    }
    // #endregion
}
