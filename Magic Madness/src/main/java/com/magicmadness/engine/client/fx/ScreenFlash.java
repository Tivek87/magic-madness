package com.magicmadness.engine.client.fx;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import com.magicmadness.config.client.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
// #endregion

// ============================================================================
// MAGIC MADNESS — ADDITIVE SCREEN IMPACT FLASH
// ============================================================================
@EventBusSubscriber(modid = MagicMadness.MODID, value = Dist.CLIENT)
public final class ScreenFlash {

    // #region 2. FLASH STATE & TRIGGERS
    private static int activeRgb = 0xFF7A3D;
    private static float maxAlpha = 0.0F;
    private static int durationTicks = 0;
    private static int ageTicks = 0;

    private ScreenFlash() {}

    public static void triggerAt(Vec3 sourcePos, double maxDistance, int rgb, float alpha, int duration) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double dist = player.position().distanceTo(sourcePos);
        if (dist >= maxDistance) {
            return;
        }
        float falloff = (float) (1.0 - (dist / maxDistance));
        trigger(rgb, alpha * falloff * falloff, duration);
    }

    public static void trigger(int rgb, float alpha, int duration) {
        double scale = ClientSettings.get(ClientSettings.SCREEN_FLASH);
        if (scale <= 0.001 || alpha <= 0.001F) {
            return;
        }
        float scaledAlpha = Mth.clamp((float) (alpha * scale), 0.0F, 0.45F);
        if (scaledAlpha >= currentAlpha(0.0F)) {
            activeRgb = rgb;
            maxAlpha = scaledAlpha;
            durationTicks = Math.max(1, duration);
            ageTicks = 0;
        }
    }

    private static float currentAlpha(float partialTick) {
        if (durationTicks <= 0 || ageTicks >= durationTicks) {
            return 0.0F;
        }
        float t = Mth.clamp((ageTicks + partialTick) / (float) durationTicks, 0.0F, 1.0F);
        return maxAlpha * (1.0F - t) * (1.0F - t);
    }
    // #endregion

    // #region 3. TICK & OVERLAY RENDER
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        if (ageTicks < durationTicks) {
            ageTicks++;
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float alpha = currentAlpha(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (alpha <= 0.003F) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        int r = (activeRgb >> 16) & 0xFF;
        int gr = (activeRgb >> 8) & 0xFF;
        int b = activeRgb & 0xFF;
        int color = FastColor.ARGB32.color(a, r, gr, b);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), color);
    }
    // #endregion
}
