package com.magicmadness.spell.client;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import com.magicmadness.config.client.ClientSettings;
import com.magicmadness.engine.client.ui.GuiShapes;
import com.magicmadness.engine.math.Ease;
import com.magicmadness.network.CastSpellPayload;
import com.magicmadness.spell.Spell;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
// #endregion

// ============================================================================
// MAGIC MADNESS — HOLD [G] SPELL RADIAL MENU, SMOOTH AIM BALL & COOLDOWN UI
// ============================================================================
@EventBusSubscriber(modid = MagicMadness.MODID, value = Dist.CLIENT)
public final class SpellRadialMenu {

    // #region 2. RADIAL GEOMETRY & SMOOTH AIM STATE
    private static final int TOTAL_SECTORS = 5;
    private static final float CENTER_RING_INNER = 21.0F;
    private static final float CENTER_RING_OUTER = 24.5F;
    private static final float AIM_BALL_ORBIT_RADIUS = 33.5F;
    private static final float AIM_BALL_RADIUS = 5.2F;
    private static final float WHEEL_INNER_RADIUS = 44.0F;
    private static final float WHEEL_OUTER_RADIUS = 88.0F;

    // Balanced responsive pad radius & sensitivity for fast yet smooth rotation
    private static final float AIM_PAD_RADIUS = 34.0F;
    private static final float AIM_DEADZONE = 4.5F;
    private static final float AIM_SENSITIVITY = 1.18F;

    private static boolean menuOpen = false;
    private static float openProgress = 0.0F;
    private static float lockedYaw = 0.0F;
    private static float lockedPitch = 0.0F;
    private static float anchorPitch = 0.0F;

    private static float aimX = 0.0F;
    private static float aimY = -AIM_PAD_RADIUS * 0.65F;
    private static float targetAngleRad = -(float) (Math.PI * 0.5);
    private static float smoothAngleRad = -(float) (Math.PI * 0.5);
    private static int hoveredSector = 0;
    private static final float[] sectorHoverAnim = new float[TOTAL_SECTORS];

    private static int killConfirmTicks = 0;

    private SpellRadialMenu() {}

    public static void triggerKillConfirm() {
        if (ClientSettings.get(ClientSettings.KILL_CONFIRM) == 1) {
            killConfirmTicks = 12;
        }
    }

    private static Spell spellAtSector(int sector) {
        return switch (sector) {
            case 0 -> Spell.FIREBALL;
            case 1 -> Spell.LIGHTNING;
            case 2 -> Spell.WIND_GUST;
            default -> null;
        };
    }
    // #endregion

    // #region 3. INPUT HANDLING, CAMERA LOCK & SMOOTH ANGULAR INTERPOLATION
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            menuOpen = false;
            openProgress = 0.0F;
            return;
        }

        if (killConfirmTicks > 0 && !mc.isPaused()) {
            killConfirmTicks--;
        }

        boolean keyHeld = mc.screen == null && ModKeybinds.SPELL_RADIAL.isDown();

        if (keyHeld && !menuOpen) {
            menuOpen = true;
            lockedYaw = player.getYRot();
            lockedPitch = player.getXRot();
            anchorPitch = Mth.clamp(lockedPitch, -80.0F, 80.0F);
            player.setXRot(anchorPitch);
            player.xRotO = anchorPitch;

            aimX = 0.0F;
            aimY = -AIM_PAD_RADIUS * 0.65F;
            targetAngleRad = -(float) (Math.PI * 0.5);
            smoothAngleRad = targetAngleRad;
            hoveredSector = 0;
            for (int i = 0; i < TOTAL_SECTORS; i++) {
                sectorHoverAnim[i] = (i == 0) ? 1.0F : 0.0F;
            }
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.22F, 1.35F);
        } else if (!keyHeld && menuOpen) {
            menuOpen = false;
            player.setYRot(lockedYaw);
            player.setXRot(lockedPitch);
            player.yRotO = lockedYaw;
            player.xRotO = lockedPitch;
            if (mc.screen == null) {
                Spell chosen = spellAtSector(hoveredSector);
                if (chosen != null) {
                    PacketDistributor.sendToServer(new CastSpellPayload(chosen.id()));
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!menuOpen) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        float dYaw = Mth.wrapDegrees(player.getYRot() - lockedYaw);
        float dPitch = player.getXRot() - anchorPitch;

        if (Math.abs(dYaw) > 1.0E-4F || Math.abs(dPitch) > 1.0E-4F) {
            aimX += dYaw * AIM_SENSITIVITY;
            aimY += dPitch * AIM_SENSITIVITY;

            float mag = Mth.sqrt(aimX * aimX + aimY * aimY);
            if (mag > AIM_PAD_RADIUS) {
                aimX = (aimX / mag) * AIM_PAD_RADIUS;
                aimY = (aimY / mag) * AIM_PAD_RADIUS;
                mag = AIM_PAD_RADIUS;
            }

            if (mag > AIM_DEADZONE) {
                targetAngleRad = (float) Mth.atan2(aimY, aimX);
                // Keep virtual cursor softly pulled toward comfortable orbit radius
                float idealMag = AIM_PAD_RADIUS * 0.68F;
                float pull = Mth.lerp(0.15F, mag, idealMag) / mag;
                aimX *= pull;
                aimY *= pull;
            }
        }

        // Lock player rotation before 3D world & hand render
        player.setYRot(lockedYaw);
        player.setXRot(anchorPitch);
        player.yRotO = lockedYaw;
        player.xRotO = anchorPitch;
        player.yHeadRot = lockedYaw;
        player.yHeadRotO = lockedYaw;

        event.setYaw(lockedYaw);
        event.setPitch(lockedPitch);
    }

    private static float wrapRadians(float angle) {
        while (angle <= -Math.PI) {
            angle += (float) (Math.PI * 2.0);
        }
        while (angle > Math.PI) {
            angle -= (float) (Math.PI * 2.0);
        }
        return angle;
    }

    private static int computeSectorFromAngle(float angleRad) {
        double step = (Math.PI * 2.0) / TOTAL_SECTORS;
        double shifted = angleRad + (Math.PI * 0.5) + (step * 0.5);
        while (shifted < 0.0) {
            shifted += Math.PI * 2.0;
        }
        while (shifted >= Math.PI * 2.0) {
            shifted -= Math.PI * 2.0;
        }
        return ((int) (shifted / step)) % TOTAL_SECTORS;
    }
    // #endregion

    // #region 4. GUI RENDERING (ABOVE HOTBAR, SMOOTH ORBITING AIM BALL & COOLDOWN UI)
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float deltaFrame = event.getPartialTick().getRealtimeDeltaTicks();

        if (menuOpen) {
            openProgress = Mth.clamp(openProgress + deltaFrame * 0.32F, 0.0F, 1.0F);
        } else {
            openProgress = Mth.clamp(openProgress - deltaFrame * 0.38F, 0.0F, 1.0F);
        }

        // Exponential Shortest-Path Angular Smoothing for Buttery 120-240 FPS Aim Ball Rotation
        float angleDiff = wrapRadians(targetAngleRad - smoothAngleRad);
        float angleSmooth = 1.0F - (float) Math.exp(-deltaFrame * 0.72F);
        smoothAngleRad = wrapRadians(smoothAngleRad + angleDiff * angleSmooth);

        int newSector = computeSectorFromAngle(smoothAngleRad);
        if (menuOpen && newSector != hoveredSector) {
            hoveredSector = newSector;
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.12F, 1.55F);
        }

        float hoverSmooth = 1.0F - (float) Math.exp(-deltaFrame * 0.65F);
        for (int i = 0; i < TOTAL_SECTORS; i++) {
            float targetHover = (i == hoveredSector) ? 1.0F : 0.0F;
            sectorHoverAnim[i] = Mth.lerp(hoverSmooth, sectorHoverAnim[i], targetHover);
        }

        GuiGraphics g = event.getGuiGraphics();
        float hudOpacity = (float) ClientSettings.get(ClientSettings.HUD_OPACITY);
        if (hudOpacity <= 0.02F) {
            return;
        }

        if (killConfirmTicks > 0) {
            renderKillConfirmCrosshair(g, partialTick);
        }

        if (openProgress > 0.01F) {
            g.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);
            renderRadialMenu(g, mc.font, partialTick, hudOpacity);
            g.pose().popPose();
            g.flush();
        }
    }

    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (openProgress > 0.05F && (event.getName().equals(VanillaGuiLayers.CROSSHAIR)
                || event.getName().equals(VanillaGuiLayers.CHAT))) {
            event.setCanceled(true);
        }
    }

    private static void renderRadialMenu(GuiGraphics g, Font font, float partialTick, float baseAlpha) {
        float scale = (float) Ease.outBack(openProgress);
        float alpha = openProgress * baseAlpha;
        float cx = g.guiWidth() * 0.5F;
        // Positioned higher so the larger wheel and info card sit cleanly above the hotbar
        float cy = g.guiHeight() * 0.40F;

        // Soft Ambient Radial Backdrop Disc
        GuiShapes.disc(g, cx, cy, 114.0F * scale, 48,
                GuiShapes.withAlpha(0x09080E, alpha * 0.76F),
                GuiShapes.withAlpha(0x09080E, 0.0F));

        float sectorStep = (float) ((Math.PI * 2.0) / TOTAL_SECTORS);
        float gapRad = 0.050F;

        // Draw All 5 Radial Sectors (Sector 0 = Fireball, Sectors 1..4 = Locked/Empty Slots)
        for (int i = 0; i < TOTAL_SECTORS; i++) {
            float centerAngle = -(float) (Math.PI * 0.5) + i * sectorStep;
            float startAngle = centerAngle - (sectorStep * 0.5F) + gapRad * 0.5F;
            float sweepAngle = sectorStep - gapRad;
            float hover = sectorHoverAnim[i];
            boolean selected = (i == hoveredSector);
            Spell spell = spellAtSector(i);

            float inR = WHEEL_INNER_RADIUS * scale;
            float outR = (WHEEL_OUTER_RADIUS + 5.0F * hover) * scale;

            if (spell != null) {
                boolean onCd = ClientSpellCooldowns.isOnCooldown(spell);
                float cdProg = ClientSpellCooldowns.progress(spell, partialTick);
                boolean isElectric = (spell == Spell.LIGHTNING);
                boolean isAir = (spell == Spell.WIND_GUST);

                int selIn = isElectric ? 0x132030 : (isAir ? 0x122624 : 0x261714);
                int selOut = isElectric ? (onCd ? 0x182A3D : 0x1B3854)
                        : (isAir ? (onCd ? 0x173330 : 0x1E4641) : (onCd ? 0x361E18 : 0x422216));
                int innerFill = GuiShapes.withAlpha(selected ? selIn : 0x15131C, alpha * 0.92F);
                int outerFill = GuiShapes.withAlpha(selected ? selOut : 0x1D1A26, alpha * 0.95F);

                GuiShapes.arc(g, cx, cy, inR, outR, startAngle, sweepAngle, 22, innerFill, outerFill);

                // Cooldown Sweep Overlay Inside Sector
                if (onCd && cdProg > 0.001F) {
                    GuiShapes.arc(g, cx, cy, inR, outR, startAngle, sweepAngle * cdProg, 20,
                            GuiShapes.withAlpha(0x08070C, alpha * 0.74F));
                    GuiShapes.arc(g, cx, cy, outR - 3.0F * scale, outR, startAngle, sweepAngle * cdProg, 20,
                            GuiShapes.withAlpha(spell.accentColor(), alpha * 0.98F));
                } else {
                    int rimColor = GuiShapes.withAlpha(spell.accentColor(), alpha * (0.55F + 0.45F * hover));
                    GuiShapes.arc(g, cx, cy, outR - 2.6F * scale, outR, startAngle, sweepAngle, 22, rimColor);
                }

                // Inner Sector Rim
                GuiShapes.arc(g, cx, cy, inR, inR + 1.5F * scale, startAngle, sweepAngle, 18,
                        GuiShapes.withAlpha(selected ? spell.accentColor() : 0x3E384F, alpha * 0.85F));

                // Pure Square Pixel-Art School Icon in Sector Center
                float iconMidR = (inR + outR) * 0.5F;
                float ix = cx + Mth.cos(centerAngle) * iconMidR;
                float iy = cy + Mth.sin(centerAngle) * iconMidR;
                float iconScale = (1.24F + 0.20F * hover) * scale;
                GuiShapes.drawSchoolIcon(g, spell.school(), ix, iy - 4.0F * scale, iconScale, onCd ? alpha * 0.45F : alpha);

                if (onCd && ClientSettings.get(ClientSettings.SHOW_COOLDOWN_NUMBERS) == 1) {
                    float secs = ClientSpellCooldowns.remainingSeconds(spell, partialTick);
                    String cdTxt = String.format(Locale.ROOT, "%.1fs", secs);
                    int cdCol = isElectric ? 0xB8EEFF : (isAir ? 0xC4FFF2 : 0xFFD08A);
                    drawCenteredText(g, font, cdTxt, (int) ix, (int) (iy + 8.0F * scale),
                            GuiShapes.withAlpha(cdCol, alpha));
                } else {
                    drawCenteredText(g, font, spell.displayName(), (int) ix, (int) (iy + 10.0F * scale),
                            GuiShapes.withAlpha(selected ? 0xFFF6E8 : 0xA8A2B8, alpha * 0.92F));
                }
            } else {
                int emptyIn = GuiShapes.withAlpha(selected ? 0x1A1724 : 0x110F18, alpha * 0.74F);
                int emptyOut = GuiShapes.withAlpha(selected ? 0x242032 : 0x161420, alpha * 0.78F);
                GuiShapes.arc(g, cx, cy, inR, outR, startAngle, sweepAngle, 18, emptyIn, emptyOut);
                GuiShapes.arc(g, cx, cy, outR - 1.5F * scale, outR, startAngle, sweepAngle, 18,
                        GuiShapes.withAlpha(selected ? 0x524A6B : 0x2A2638, alpha * 0.65F));

                float slotMidR = (inR + outR) * 0.5F;
                float sx = cx + Mth.cos(centerAngle) * slotMidR;
                float sy = cy + Mth.sin(centerAngle) * slotMidR;
                GuiShapes.ring(g, sx, sy, 3.6F * scale, 5.0F * scale, 14,
                        GuiShapes.withAlpha(selected ? 0x645A82 : 0x322D42, alpha * 0.75F));
            }
        }

        // 4. Center Circle Ring & Inner Hub
        float cIn = CENTER_RING_INNER * scale;
        float cOut = CENTER_RING_OUTER * scale;
        Spell hoveredSpell = spellAtSector(hoveredSector);
        int activeAccent = hoveredSpell != null ? hoveredSpell.accentColor() : 0x7A7294;

        GuiShapes.disc(g, cx, cy, cIn, 28, GuiShapes.withAlpha(0x0E0C14, alpha * 0.95F));
        GuiShapes.ring(g, cx, cy, cIn, cOut, 32, GuiShapes.withAlpha(0x3B354C, alpha * 0.90F));

        // Subtle Orbit Track Ring Outside the Center Circle Ring
        float orbitR = AIM_BALL_ORBIT_RADIUS * scale;
        GuiShapes.ring(g, cx, cy, orbitR - 0.45F * scale, orbitR + 0.45F * scale, 36,
                GuiShapes.withAlpha(0x2B2738, alpha * 0.55F));

        // Smooth Directional Highlight Arc on Center Ring Tracking the Aim Ball
        GuiShapes.arc(g, cx, cy, cIn - 0.3F, cOut + 0.7F, smoothAngleRad - 0.45F, 0.90F, 14,
                GuiShapes.withAlpha(activeAccent, alpha));

        // Center Hub Dot
        GuiShapes.disc(g, cx, cy, 2.0F * scale, 10, GuiShapes.withAlpha(0xEAE4F2, alpha * 0.80F));

        // 5. Smooth Orbiting Aim Ball Outside the Center Circle Ring
        float ballX = cx + Mth.cos(smoothAngleRad) * orbitR;
        float ballY = cy + Mth.sin(smoothAngleRad) * orbitR;
        float ringEdgeX = cx + Mth.cos(smoothAngleRad) * cOut;
        float ringEdgeY = cy + Mth.sin(smoothAngleRad) * cOut;

        GuiShapes.line(g, ringEdgeX, ringEdgeY, ballX, ballY, 1.6F * scale,
                GuiShapes.withAlpha(activeAccent, alpha * 0.85F));

        float bRad = AIM_BALL_RADIUS * scale;
        GuiShapes.disc(g, ballX, ballY, bRad * 1.8F, 18,
                GuiShapes.withAlpha(activeAccent, alpha * 0.40F),
                GuiShapes.withAlpha(activeAccent, 0.0F));
        GuiShapes.disc(g, ballX, ballY, bRad, 16,
                GuiShapes.withAlpha(activeAccent, alpha));
        GuiShapes.disc(g, ballX, ballY, bRad * 0.50F, 12,
                GuiShapes.withAlpha(0xFFF9E6, alpha));

        // 6. Clean Vanilla+ Spell & Cooldown Info Card (Above Hotbar)
        int cardTopY = Math.min((int) (cy + 96.0F * scale), g.guiHeight() - 74);
        renderRadialInfoCard(g, font, (int) cx, cardTopY, hoveredSpell, partialTick, alpha);
    }

    private static void renderRadialInfoCard(GuiGraphics g, Font font, int cx, int topY,
                                             Spell spell, float partialTick, float alpha) {
        int cardW = 196;
        int cardH = 48;
        int x0 = cx - cardW / 2;
        int y0 = topY;

        // Vanilla+ Tooltip-Style Framed Panel
        int bg = GuiShapes.withAlpha(0x110E17, alpha * 0.94F);
        int outerBorder = GuiShapes.withAlpha(0x07050A, alpha * 0.95F);
        int innerBorder = GuiShapes.withAlpha(spell != null ? spell.accentColor() : 0x3D3750, alpha * 0.85F);

        g.fill(x0 - 1, y0 - 1, x0 + cardW + 1, y0 + cardH + 1, outerBorder);
        g.fill(x0, y0, x0 + cardW, y0 + cardH, bg);
        g.fill(x0, y0, x0 + cardW, y0 + 1, innerBorder);
        g.fill(x0, y0 + cardH - 1, x0 + cardW, y0 + cardH, GuiShapes.withAlpha(0x2A2438, alpha * 0.90F));
        g.fill(x0, y0, x0 + 1, y0 + cardH, innerBorder);
        g.fill(x0 + cardW - 1, y0, x0 + cardW, y0 + cardH, innerBorder);

        if (spell != null) {
            int titleCol = (spell == Spell.LIGHTNING) ? 0xC8F0FF
                    : (spell == Spell.WIND_GUST ? 0xC8FFF2 : 0xFFE8C8);
            drawCenteredText(g, font, spell.displayName().toUpperCase(Locale.ROOT), cx, y0 + 6, GuiShapes.withAlpha(titleCol, alpha));

            double dmg = spell.damage();
            String dmgStr = (Math.abs(dmg - Math.rint(dmg)) < 0.05)
                    ? String.format(Locale.ROOT, "%.0f", dmg)
                    : String.format(Locale.ROOT, "%.1f", dmg);
            String stats = String.format(Locale.ROOT, "%.0fm Range  •  %s Damage",
                    spell.rangeBlocks(), dmgStr);
            drawCenteredText(g, font, stats, cx, y0 + 17, GuiShapes.withAlpha(0xB4ACBF, alpha));

            int barW = cardW - 20;
            int barX = x0 + 10;
            int barY = y0 + 29;
            int barH = 12;
            g.fill(barX, barY, barX + barW, barY + barH, GuiShapes.withAlpha(0x1B1724, alpha * 0.95F));

            boolean onCd = ClientSpellCooldowns.isOnCooldown(spell);
            if (onCd) {
                float cdProg = ClientSpellCooldowns.progress(spell, partialTick);
                float remSec = ClientSpellCooldowns.remainingSeconds(spell, partialTick);
                float totSec = spell.cooldownTicks() / 20.0F;
                int fillW = Math.round(barW * (1.0F - cdProg));
                g.fill(barX, barY, barX + fillW, barY + barH, GuiShapes.withAlpha(0xC8521C, alpha * 0.88F));
                String cdLabel = String.format(Locale.ROOT, "Cooldown: %.1fs / %.1fs", remSec, totSec);
                drawCenteredText(g, font, cdLabel, cx, barY + 2, GuiShapes.withAlpha(0xFFE0B2, alpha));
            } else {
                g.fill(barX, barY, barX + barW, barY + barH, GuiShapes.withAlpha(0x285436, alpha * 0.88F));
                drawCenteredText(g, font, "Ready — Release [G] to Cast", cx, barY + 2,
                        GuiShapes.withAlpha(0x98FFBE, alpha));
            }
        } else {
            drawCenteredText(g, font, "Empty Spell Slot", cx, y0 + 11, GuiShapes.withAlpha(0x9A92AB, alpha));
            drawCenteredText(g, font, "Aim at a Spell Sector to cast", cx, y0 + 25,
                    GuiShapes.withAlpha(0x68607A, alpha));
        }
    }

    private static void renderKillConfirmCrosshair(GuiGraphics g, float partialTick) {
        float t = Mth.clamp((killConfirmTicks - partialTick) / 12.0F, 0.0F, 1.0F);
        float cx = g.guiWidth() * 0.5F;
        float cy = g.guiHeight() * 0.5F;
        float inner = 4.5F + (1.0F - t) * 2.5F;
        float outer = inner + 4.5F;
        int color = GuiShapes.withAlpha(0xFF3B30, t * 0.92F);

        GuiShapes.line(g, cx - outer, cy - outer, cx - inner, cy - inner, 1.6F, color);
        GuiShapes.line(g, cx + outer, cy - outer, cx + inner, cy - inner, 1.6F, color);
        GuiShapes.line(g, cx - outer, cy + outer, cx - inner, cy + inner, 1.6F, color);
        GuiShapes.line(g, cx + outer, cy + outer, cx + inner, cy + inner, 1.6F, color);
    }

    private static void drawCenteredText(GuiGraphics g, Font font, String text, int cx, int y, int argb) {
        int width = font.width(text);
        g.drawString(font, text, cx - width / 2, y, argb, true);
    }
    // #endregion
}
