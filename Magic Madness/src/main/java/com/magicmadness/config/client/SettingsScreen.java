package com.magicmadness.config.client;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import com.magicmadness.network.WorldSettingsEditPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
// #endregion

// ============================================================================
// MAGIC MADNESS — MULTI-TAB SEARCHABLE CONFIG SCREEN (CLIENT & SERVER)
// ============================================================================
public class SettingsScreen extends DirtBackgroundScreen {

    // #region 2. CONSTANTS & STATE
    private static final String PREFIX = "config." + MagicMadness.MODID + ".";
    private static final int MAX_WIDTH = 520;
    private static final int TABS_Y = 22;
    private static final int SEARCH_Y = 44;
    private static final int LIST_TOP = 64;
    private static final int HELP_HEIGHT = 32;
    private static final int FOOTER = 30;
    private static final int CHANGED = 0xF2C84B;

    @Nullable
    private final Screen lastScreen;
    private final Kind kind;
    private final List<SettingsPages.Page> pages;
    private final Map<ConfigNumber, SettingsPages.Page> pageOf = new IdentityHashMap<>();
    private final Map<ConfigNumber, Double> edits = new IdentityHashMap<>();
    private final Set<String> collapsed = new HashSet<>();
    private final List<Button> tabs = new ArrayList<>();
    private int tab;
    private String query = "";
    private SettingsList list;
    @Nullable
    private ConfigNumber pointed;
    private int panelLeft;
    private int panelWidth;
    private boolean rebuildLater;
    private boolean keepScrollLater;
    private boolean retabLater;

    public enum Kind { CLIENT, SERVER }

    public SettingsScreen(@Nullable Screen lastScreen, Kind kind, int tab) {
        super(Component.translatable(PREFIX + "title." + kind.name().toLowerCase(Locale.ROOT)));
        this.lastScreen = lastScreen;
        this.kind = kind;
        this.pages = kind == Kind.CLIENT ? SettingsPages.clientPages() : SettingsPages.serverPages();
        this.tab = Mth.clamp(tab, 0, this.pages.size() - 1);
        for (SettingsPages.Page page : this.pages) {
            for (SettingsPages.Section section : page.sections()) {
                for (SettingsPages.Group group : section.groups()) {
                    for (ConfigNumber number : group.numbers()) {
                        this.pageOf.put(number, page);
                    }
                }
            }
        }
    }
    // #endregion

    // #region 3. WIDGET INITIALIZATION & LIST REBUILD
    @Override
    protected void init() {
        if (this.kind == Kind.SERVER && !SettingsPages.canOpenServer()) {
            this.onClose();
            return;
        }
        this.panelWidth = Math.min(this.width - 12, MAX_WIDTH);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        int inner = this.panelWidth - 12;
        int left = this.panelLeft + 6;
        int right = left + inner;

        this.tabs.clear();
        int count = this.pages.size();
        int tabWidth = (inner - (count - 1) * 2) / count;
        for (int i = 0; i < count; i++) {
            int index = i;
            Button button = Button.builder(this.tabLabel(i), pressed -> this.selectTab(index))
                    .bounds(left + i * (tabWidth + 2), TABS_Y, tabWidth, 18).build();
            this.tabs.add(this.addRenderableWidget(button));
        }

        EditBox search = new EditBox(this.font, left + 1, SEARCH_Y + 1, inner - 2 * 64 - 6, 16,
                Component.translatable(PREFIX + "search"));
        search.setHint(Component.translatable(PREFIX + "search.hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(40);
        search.setValue(this.query);
        search.setResponder(text -> {
            this.query = text;
            this.rebuildLater(false);
        });
        this.addRenderableWidget(search);

        this.addRenderableWidget(Button.builder(Component.translatable(PREFIX + "expand"), pressed -> {
            this.collapsed.clear();
            this.rebuildLater(true);
        }).bounds(right - 2 * 64 - 2, SEARCH_Y, 64, 18)
                .tooltip(Tooltip.create(Component.translatable(PREFIX + "expand.desc"))).build());

        this.addRenderableWidget(Button.builder(Component.translatable(PREFIX + "collapse"), pressed -> {
            SettingsPages.Page page = this.pages.get(this.tab);
            for (int i = 0; i < page.sections().size(); i++) {
                this.collapsed.add(this.key(this.tab, i));
            }
            this.rebuildLater(false);
        }).bounds(right - 64, SEARCH_Y, 64, 18)
                .tooltip(Tooltip.create(Component.translatable(PREFIX + "collapse.desc"))).build());

        int buttonsY = this.height - 26;
        int buttonWidth = Math.min(100, (inner - 3 * 4) / 4);
        int start = this.width / 2 - (4 * buttonWidth + 3 * 4) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable(PREFIX + "defaults"), pressed -> this.defaults())
                .bounds(start, buttonsY, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable(PREFIX + "defaults.desc"))).build());
        this.addRenderableWidget(Button.builder(Component.translatable(PREFIX + "apply"), pressed -> this.apply())
                .bounds(start + buttonWidth + 4, buttonsY, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable(PREFIX + "apply.desc"))).build());
        this.addRenderableWidget(Button.builder(Component.translatable(PREFIX + "save"), pressed -> {
            this.apply();
            this.onClose();
        }).bounds(start + 2 * (buttonWidth + 4), buttonsY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, pressed -> this.onClose())
                .bounds(start + 3 * (buttonWidth + 4), buttonsY, buttonWidth, 20).build());
        this.rebuild(true);
    }

    private void rebuild(boolean keepScroll) {
        double scroll = this.list == null ? 0.0 : this.list.getScrollAmount();
        if (this.list != null) {
            this.removeWidget(this.list);
        }
        int left = this.panelLeft + 6;
        int bottom = this.height - FOOTER - HELP_HEIGHT;
        this.list = new SettingsList(this.minecraft, this, this.blocks(), left, LIST_TOP,
                this.panelWidth - 12, bottom - LIST_TOP);
        this.addRenderableWidget(this.list);
        if (keepScroll) {
            this.list.setClampedScrollAmount(scroll);
        }
        for (int i = 0; i < this.tabs.size(); i++) {
            this.tabs.get(i).setMessage(this.tabLabel(i));
        }
    }
    // #endregion

    // #region 4. SEARCH FILTERING, EDITS & SAVING
    private List<SettingsList.Block> blocks() {
        List<SettingsList.Block> blocks = new ArrayList<>();
        String wanted = this.query.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            SettingsPages.Page page = this.pages.get(this.tab);
            for (int i = 0; i < page.sections().size(); i++) {
                SettingsPages.Section section = page.sections().get(i);
                String key = this.key(this.tab, i);
                blocks.add(new SettingsList.Block(key, section.title(), section.hint(), page.color(), true,
                        this.collapsed.contains(key), section.groups()));
            }
            return blocks;
        }
        for (SettingsPages.Page page : this.pages) {
            for (SettingsPages.Section section : page.sections()) {
                boolean all = matches(section.title(), wanted);
                List<SettingsPages.Group> groups = new ArrayList<>();
                for (SettingsPages.Group group : section.groups()) {
                    boolean whole = all || group.title() != null && matches(group.title(), wanted);
                    List<ConfigNumber> numbers = new ArrayList<>();
                    for (ConfigNumber number : group.numbers()) {
                        if (whole || matches(number.label(), wanted) || matches(number.description(), wanted)) {
                            numbers.add(number);
                        }
                    }
                    if (!numbers.isEmpty()) {
                        groups.add(new SettingsPages.Group(group.title(), numbers));
                    }
                }
                if (!groups.isEmpty()) {
                    Component title = Component.empty().append(page.title()).append(" › ").append(section.title());
                    blocks.add(new SettingsList.Block("search", title, section.hint(), page.color(),
                            false, false, groups));
                }
            }
        }
        return blocks;
    }

    private static boolean matches(Component text, String wanted) {
        return text.getString().toLowerCase(Locale.ROOT).contains(wanted);
    }

    private String key(int page, int section) {
        return page + ":" + section;
    }

    void toggle(String key) {
        if (!this.collapsed.remove(key)) {
            this.collapsed.add(key);
        }
        this.rebuildLater(true);
    }

    private void selectTab(int index) {
        this.tab = index;
        this.query = "";
        this.retabLater = true;
    }

    private void rebuildLater(boolean keepScroll) {
        this.keepScrollLater = this.rebuildLater ? this.keepScrollLater && keepScroll : keepScroll;
        this.rebuildLater = true;
    }

    private Component tabLabel(int index) {
        SettingsPages.Page page = this.pages.get(index);
        boolean changed = this.edits.keySet().stream().anyMatch(number -> this.pageOf.get(number) == page);
        Component label = changed ? Component.empty().append(page.title()).append(" •") : page.title();
        return index == this.tab && this.query.isBlank()
                ? label.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)
                : label;
    }

    double value(ConfigNumber number) {
        Double edited = this.edits.get(number);
        return edited != null ? edited : number.clamp(number.stored().getAsDouble());
    }

    void set(ConfigNumber number, double value) {
        if (Math.abs(value - number.clamp(number.stored().getAsDouble())) < 1.0E-9) {
            this.edits.remove(number);
        } else {
            this.edits.put(number, value);
        }
        for (int i = 0; i < this.tabs.size(); i++) {
            this.tabs.get(i).setMessage(this.tabLabel(i));
        }
    }

    boolean editable(ConfigNumber number) {
        SettingsPages.Page page = this.pageOf.get(number);
        return page != null && page.editable();
    }

    void pointAt(@Nullable ConfigNumber number) {
        this.pointed = number;
    }

    private void defaults() {
        for (ConfigNumber number : this.list.numbers()) {
            this.set(number, number.defaultValue());
        }
        this.rebuildLater(true);
    }

    private void apply() {
        Set<SettingsPages.Page> touched = new HashSet<>();
        List<WorldSettingsEditPayload.Entry> sent = new ArrayList<>();
        boolean remote = this.minecraft != null && this.minecraft.level != null
                && !this.minecraft.hasSingleplayerServer();
        for (Map.Entry<ConfigNumber, Double> edit : this.edits.entrySet()) {
            ConfigNumber number = edit.getKey();
            SettingsPages.Page page = this.pageOf.get(number);
            if (page == null || !page.editable()) {
                continue;
            }
            number.store().accept(edit.getValue());
            if (page.world() && remote) {
                sent.add(new WorldSettingsEditPayload.Entry(number.file(), number.path(), edit.getValue()));
            } else {
                touched.add(page);
            }
        }
        for (SettingsPages.Page page : touched) {
            page.save().run();
        }
        if (!sent.isEmpty()) {
            PacketDistributor.sendToServer(new WorldSettingsEditPayload(sent));
        }
        this.edits.clear();
        this.rebuildLater(true);
    }
    // #endregion

    // #region 5. RENDERING & FOOTER HELP
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null && this.minecraft.level != null) {
            this.renderTransparentBackground(graphics);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
        drawPanel(graphics, this.panelLeft, 4, this.panelWidth, this.height - 8, PANEL_BORDER);
        this.drawBigCenteredString(graphics, this.title, this.width / 2, 8, 1.1F, 0xFFFFD255);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.retabLater) {
            this.retabLater = false;
            this.rebuildLater = false;
            this.list = null;
            this.rebuildWidgets();
        } else if (this.rebuildLater) {
            this.rebuildLater = false;
            this.rebuild(this.keepScrollLater);
        }
        this.pointed = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderHelp(graphics);
    }

    private void renderHelp(GuiGraphics graphics) {
        int center = this.width / 2;
        int left = this.panelLeft + 8;
        int width = this.panelWidth - 16;
        int top = this.height - FOOTER - HELP_HEIGHT + 3;
        drawDivider(graphics, left - 2, top - 2, width + 4);
        Component line;
        int color;
        SettingsPages.Page page = this.pages.get(this.tab);
        if (this.pointed != null) {
            line = this.pointed.description().getString().isEmpty()
                    ? this.pointed.label()
                    : this.pointed.description();
            color = TEXT_COLOR;
        } else if (!page.editable()) {
            line = Component.translatable(PREFIX + "server_decides");
            color = NOTICE_COLOR;
        } else {
            line = Component.translatable(PREFIX + (page.world() ? "world_help" : "client_help"));
            color = MUTED_COLOR;
        }
        List<FormattedCharSequence> lines = this.font.split(line, width);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawString(this.font, lines.get(i), left, top + i * 10, color);
        }
        if (!this.edits.isEmpty()) {
            Component changed = Component.translatable(PREFIX + "unsaved", this.edits.size());
            graphics.drawString(this.font, changed, left + width - this.font.width(changed), top + 20, CHANGED);
        }
        if (this.list != null && this.list.children().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable(PREFIX + "search.none"), center,
                    LIST_TOP + 12, MUTED_COLOR);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.lastScreen);
        }
    }
    // #endregion
}
