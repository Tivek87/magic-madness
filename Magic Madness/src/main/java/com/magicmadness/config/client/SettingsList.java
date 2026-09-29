package com.magicmadness.config.client;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
// #endregion

// ============================================================================
// MAGIC MADNESS — SCROLLABLE COLLAPSIBLE CONFIG LIST WIDGET
// ============================================================================
final class SettingsList extends ContainerObjectSelectionList<SettingsList.Row> {

    // #region 2. CONSTANTS & BLOCK RECORD
    static final int ROW_HEIGHT = 20;
    private static final String PREFIX = "config." + MagicMadness.MODID + ".";
    private static final Pattern NUMBER = Pattern.compile("-?[0-9]*\\.?[0-9]*");
    private static final int TEXT = 0xFFFFFF;
    private static final int CHANGED = 0xF2C84B;
    private static final int MEANING = 0x7CF29C;
    private static final int WRONG = 0xFF6464;
    private static final int GROUP = 0x9CC8A8;
    private static final int LINE = 0x40FFFFFF;
    private static final int HOVER = 0x18FFFFFF;

    record Block(String key, Component title, @Nullable Component hint, int color, boolean collapsible,
                 boolean collapsed, List<SettingsPages.Group> groups) {
        int count() {
            int count = 0;
            for (SettingsPages.Group group : this.groups) {
                count += group.numbers().size();
            }
            return count;
        }
    }

    private final SettingsScreen screen;
    private final List<ConfigNumber> numbers = new ArrayList<>();
    // #endregion

    // #region 3. CONSTRUCTOR & LIST METRICS
    SettingsList(Minecraft minecraft, SettingsScreen screen, List<Block> blocks, int x, int y,
                 int width, int height) {
        super(minecraft, width, height, y, ROW_HEIGHT);
        this.screen = screen;
        this.setX(x);
        for (Block block : blocks) {
            this.addEntry(new TitleRow(block));
            if (block.collapsed()) {
                continue;
            }
            for (SettingsPages.Group group : block.groups()) {
                if (group.title() != null) {
                    this.addEntry(new GroupRow(group.title()));
                }
                for (ConfigNumber number : group.numbers()) {
                    this.numbers.add(number);
                    this.addEntry(new NumberRow(number));
                }
            }
        }
    }

    List<ConfigNumber> numbers() {
        return this.numbers;
    }

    @Override
    public int getRowWidth() {
        return this.width - 16;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width - 6;
    }

    @Override
    protected void renderListBackground(GuiGraphics graphics) {
        graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x50000000);
    }

    @Override
    protected void renderListSeparators(GuiGraphics graphics) {
        graphics.fill(this.getX(), this.getY() - 1, this.getRight(), this.getY(), LINE);
        graphics.fill(this.getX(), this.getBottom(), this.getRight(), this.getBottom() + 1, LINE);
    }

    private static FormattedCharSequence fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        String cut = font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("...")));
        return Component.literal(cut + "...").withStyle(text.getStyle()).getVisualOrderText();
    }

    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {}
    // #endregion

    // #region 4. TITLE ROW, GROUP ROW & NUMBER ROW
    private final class TitleRow extends Row {
        private final Block block;
        private final Component title;
        private final Component hint;

        TitleRow(Block block) {
            this.block = block;
            String arrow = !block.collapsible() ? "" : block.collapsed() ? "▶ " : "▼ ";
            this.title = Component.literal(arrow).append(block.title().copy().withStyle(ChatFormatting.BOLD))
                    .append(Component.literal("  (" + block.count() + ")").withStyle(ChatFormatting.GRAY));
            this.hint = block.hint() == null ? Component.empty() : block.hint();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = SettingsList.this.minecraft.font;
            if (hovering && this.block.collapsible()) {
                graphics.fill(left - 2, top, left + width + 2, top + height, HOVER);
            }
            int y = top + (height - 8) / 2;
            int hintWidth = font.width(this.hint);
            graphics.drawString(font, fit(font, this.title, width - hintWidth - 8), left, y,
                    0xFF000000 | this.block.color());
            graphics.drawString(font, this.hint, left + width - hintWidth, y, 0xFFA8A090);
            graphics.fill(left, top + height - 1, left + width, top + height,
                    0x80000000 | (this.block.color() & 0xFFFFFF));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.block.collapsible() || button != 0) {
                return false;
            }
            SettingsList.this.screen.toggle(this.block.key());
            return true;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(narration(this.title));
        }
    }

    private final class GroupRow extends Row {
        private final Component title;

        GroupRow(Component title) {
            this.title = title;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = SettingsList.this.minecraft.font;
            int y = top + height - 9;
            FormattedCharSequence text = fit(font, this.title, width - 12);
            graphics.drawString(font, text, left + 6, y, 0xFF000000 | GROUP);
            int end = left + 10 + font.width(text);
            if (end < left + width) {
                graphics.fill(end, y + 4, left + width, y + 5, LINE);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(narration(this.title));
        }
    }

    private final class NumberRow extends Row {
        private final ConfigNumber number;
        private final Button minus;
        private final Button plus;
        private final Button reset;
        private final EditBox box;
        private double value;
        private boolean valid = true;

        NumberRow(ConfigNumber number) {
            this.number = number;
            this.value = SettingsList.this.screen.value(number);
            Font font = SettingsList.this.minecraft.font;
            Component steps = Component.translatable(PREFIX + "steps");
            this.minus = Button.builder(Component.literal("-"), button -> this.nudge(-1)).size(14, 16)
                    .tooltip(Tooltip.create(steps)).build();
            this.plus = Button.builder(Component.literal("+"), button -> this.nudge(1)).size(14, 16)
                    .tooltip(Tooltip.create(steps)).build();
            this.reset = Button.builder(Component.literal("↺"), button -> this.set(number.defaultValue()))
                    .size(14, 16)
                    .tooltip(Tooltip.create(Component.translatable(PREFIX + "reset",
                            number.format(number.defaultValue()), number.meaning(number.defaultValue()))))
                    .build();
            this.box = new EditBox(font, 0, 0, 44, 16, number.label());
            this.box.setMaxLength(12);
            this.box.setFilter(text -> NUMBER.matcher(text).matches());
            this.box.setValue(number.format(this.value));
            this.box.setResponder(this::typed);
            boolean editable = SettingsList.this.screen.editable(number);
            this.minus.active = editable;
            this.plus.active = editable;
            this.box.setEditable(editable);
        }

        private void typed(String text) {
            try {
                double typed = Double.parseDouble(text);
                this.valid = typed >= this.number.min() - 1.0E-9 && typed <= this.number.max() + 1.0E-9;
                if (this.valid) {
                    this.value = this.number.clamp(typed);
                    SettingsList.this.screen.set(this.number, this.value);
                }
            } catch (NumberFormatException wrong) {
                this.valid = false;
            }
            this.box.setTextColor(this.valid ? 0xE0E0E0 : WRONG);
        }

        void set(double value) {
            this.value = this.number.clamp(value);
            this.box.setValue(this.number.format(this.value));
            SettingsList.this.screen.set(this.number, this.value);
        }

        private void nudge(int way) {
            int steps = Screen.hasShiftDown() ? 10 : 1;
            double next = Math.round((this.value + way * steps * this.number.step()) * 1000.0) / 1000.0;
            this.set(next);
        }

        private boolean changed() {
            return Math.abs(this.value - this.number.defaultValue()) > 1.0E-9;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = SettingsList.this.minecraft.font;
            if (hovering) {
                graphics.fill(left - 2, top, left + width + 2, top + height, HOVER);
                SettingsList.this.screen.pointAt(this.number);
            }
            int labelWidth = Math.max(80, (int) (width * 0.40));
            int x = left + labelWidth + 4;
            this.minus.setPosition(x, top + 2);
            this.box.setPosition(x + 16, top + 2);
            this.plus.setPosition(x + 62, top + 2);
            this.reset.setPosition(x + 78, top + 2);
            this.reset.visible = this.changed() && SettingsList.this.screen.editable(this.number);
            this.minus.render(graphics, mouseX, mouseY, partialTick);
            this.box.render(graphics, mouseX, mouseY, partialTick);
            this.plus.render(graphics, mouseX, mouseY, partialTick);
            this.reset.render(graphics, mouseX, mouseY, partialTick);

            int textY = top + (height - 8) / 2;
            graphics.drawString(font, fit(font, this.number.label(), labelWidth - 4), left, textY,
                    0xFF000000 | (this.changed() ? CHANGED : TEXT));
            int meaningX = x + 96;
            Component meaning = this.valid ? this.number.meaning(this.value)
                    : Component.translatable(PREFIX + "range", this.number.format(this.number.min()),
                            this.number.format(this.number.max()));
            graphics.drawString(font, fit(font, meaning, left + width - meaningX), meaningX, textY,
                    0xFF000000 | (this.valid ? MEANING : WRONG));

            if (mouseX >= left && mouseX < left + labelWidth && mouseY >= top && mouseY < top + height) {
                SettingsList.this.screen.setTooltipForNextRenderPass(this.tooltip(font));
            }
        }

        private List<FormattedCharSequence> tooltip(Font font) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(this.number.label().copy().withStyle(ChatFormatting.WHITE).getVisualOrderText());
            if (!this.number.description().getString().isEmpty()) {
                lines.addAll(font.split(this.number.description().copy().withStyle(ChatFormatting.GRAY), 220));
            }
            double standard = this.number.defaultValue();
            lines.add(Component.translatable(PREFIX + "default", this.number.format(standard),
                    this.number.meaning(standard)).withStyle(ChatFormatting.GREEN).getVisualOrderText());
            lines.add(Component.translatable(PREFIX + "range", this.number.format(this.number.min()),
                    this.number.format(this.number.max())).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
            return lines;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.minus, this.box, this.plus, this.reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.minus, this.box, this.plus, this.reset);
        }
    }

    private static NarratableEntry narration(Component title) {
        return new NarratableEntry() {
            @Override
            public NarratableEntry.NarrationPriority narrationPriority() {
                return NarratableEntry.NarrationPriority.HOVERED;
            }

            @Override
            public void updateNarration(NarrationElementOutput output) {
                output.add(NarratedElementType.TITLE, title);
            }
        };
    }
    // #endregion
}
