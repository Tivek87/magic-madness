package com.magicmadness.config.client;

// #region 1. IMPORTS
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
// #endregion

// ============================================================================
// MAGIC MADNESS — BASE CONFIG SCREEN RENDERING HELPERS
// ============================================================================
public abstract class DirtBackgroundScreen extends Screen {

    // #region 2. THEME COLORS & CONSTANTS
    public static final int PANEL_FILL = 0xB80A0C10;
    public static final int PANEL_BORDER = 0xFF4A4438;
    protected static final int TEXT_COLOR = 0xFFFFFF;
    public static final int MUTED_COLOR = 0xA8A090;
    protected static final int NOTICE_COLOR = 0xF2C84B;
    protected static final int DIVIDER_COLOR = 0x60FFFFFF;

    private static final ResourceLocation DIRT_BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/block/dirt.png");
    private static final int BACKGROUND_TILE = 32;
    private static final float BACKGROUND_DIM = 0.25F;

    protected DirtBackgroundScreen(Component title) {
        super(title);
    }
    // #endregion

    // #region 3. PANEL & TEXT DRAWING
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);
        guiGraphics.setColor(BACKGROUND_DIM, BACKGROUND_DIM, BACKGROUND_DIM, 1.0F);
        guiGraphics.blit(DIRT_BACKGROUND, 0, 0, 0, 0.0F, 0.0F, this.width, this.height,
                BACKGROUND_TILE, BACKGROUND_TILE);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.fillGradient(0, this.height / 2, this.width, this.height, 0x00000000, 0x80000000);
    }

    protected static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int borderColor) {
        guiGraphics.fill(x, y, x + width, y + height, PANEL_FILL);
        guiGraphics.renderOutline(x, y, width, height, borderColor);
    }

    protected void drawBigCenteredString(GuiGraphics guiGraphics, Component text, int centerX, int y,
                                         float scale, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawCenteredString(this.font, text, 0, 0, color);
        guiGraphics.pose().popPose();
    }

    protected static void drawDivider(GuiGraphics guiGraphics, int x, int y, int width) {
        guiGraphics.fill(x, y, x + width, y + 1, DIVIDER_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
    // #endregion
}
