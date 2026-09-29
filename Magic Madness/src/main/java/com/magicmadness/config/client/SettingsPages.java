package com.magicmadness.config.client;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import com.magicmadness.config.ModConfigs;
import com.magicmadness.config.PowerRules;
import com.magicmadness.config.SpellConfig;
import com.magicmadness.config.Unit;
import com.magicmadness.config.WorldSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
// #endregion

// ============================================================================
// MAGIC MADNESS — CLIENT & SERVER SPELL CONFIG PAGES BUILDER & OWNER GATE
// ============================================================================
public final class SettingsPages {

    // #region 2. RECORDS & OWNER ACCESS CHECKS
    private static final String PREFIX = "config." + MagicMadness.MODID + ".";

    private SettingsPages() {}

    public record Page(Component title, int color, List<Section> sections, boolean editable,
                       boolean world, Runnable save) {}

    public record Section(Component title, @Nullable Component hint, List<Group> groups) {}

    public record Group(@Nullable Component title, List<ConfigNumber> numbers) {}

    public static List<Page> clientPages() {
        return List.of(client());
    }

    public static List<Page> serverPages() {
        WorldSettings.ensureLocalDefaultsLoaded();
        List<Page> pages = new ArrayList<>();
        pages.add(general());
        for (SpellConfig.SpellSheet sheet : SpellConfig.sheets().values()) {
            pages.add(spell(sheet));
        }
        return pages;
    }

    public static boolean serverEditable() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.hasSingleplayerServer()
                || (minecraft.player != null && PowerRules.isOwner(minecraft.player.getGameProfile()));
    }

    public static boolean canOpenServer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return true;
        }
        return serverEditable();
    }

    public static boolean worldEditable(ModConfigSpec spec) {
        return spec.isLoaded() && canOpenServer();
    }
    // #endregion

    // #region 3. GENERAL SPELL RULES, PER-SPELL & CLIENT SPELL PAGES
    public static Page general() {
        ModConfigSpec spec = PowerRules.SPEC;
        String file = ModConfigs.serverFile("general");
        List<ConfigNumber> numbers = List.of(
                fromSpec(spec, file, "general", "damageMultiplier", PowerRules.DAMAGE, Unit.STRENGTH, 0.1),
                fromSpec(spec, file, "general", "cooldownMultiplier", PowerRules.COOLDOWNS, Unit.STRENGTH, 0.1),
                fromSpec(spec, file, "general", "knockbackMultiplier", PowerRules.KNOCKBACK, Unit.STRENGTH, 0.1),
                fromSpec(spec, file, "general", "rangeMultiplier", PowerRules.RANGE, Unit.STRENGTH, 0.1),
                fromSpec(spec, file, "general", "radiusMultiplier", PowerRules.RADIUS, Unit.STRENGTH, 0.1),
                fromSpec(spec, file, "general", "breakBlocks", PowerRules.BREAK_BLOCKS, Unit.SWITCH, 1.0),
                fromSpec(spec, file, "general", "maxBlockHardness", PowerRules.MAX_BLOCK_HARDNESS, Unit.HARDNESS, 0.5),
                fromSpec(spec, file, "general", "friendlyFire", PowerRules.FRIENDLY_FIRE, Unit.SWITCH, 1.0));
        Section section = new Section(Component.translatable(PREFIX + "general.spells"), null,
                List.of(new Group(null, numbers)));

        List<ConfigNumber> allowed = new ArrayList<>();
        for (Map.Entry<String, ModConfigSpec.IntValue> entry : PowerRules.allowedSpells().entrySet()) {
            allowed.add(fromSpec(spec, file, "general", entry.getKey(), entry.getValue(), Unit.SWITCH, 1.0));
        }
        Section chosen = new Section(Component.translatable(PREFIX + "general.allowed_spells"), null,
                List.of(new Group(null, allowed)));

        return new Page(Component.translatable(PREFIX + "general"), 0x9DFF8A, List.of(section, chosen),
                worldEditable(spec), true, () -> WorldSettings.saveSpec(file, spec));
    }

    public static Page spell(SpellConfig.SpellSheet sheet) {
        ModConfigSpec spec = sheet.spec();
        String file = ModConfigs.serverFile(sheet.id());
        Map<String, List<ConfigNumber>> bySection = new LinkedHashMap<>();
        Map<String, String> hints = new LinkedHashMap<>();
        for (SpellConfig.Entry entry : sheet.entries()) {
            ConfigNumber num = fromSpec(spec, file, sheet.id(), entry.key(), entry.value(), entry.unit(), entry.step());
            bySection.computeIfAbsent(entry.section(), k -> new ArrayList<>()).add(num);
            hints.putIfAbsent(entry.section(), entry.hint());
        }
        List<Section> sections = new ArrayList<>();
        for (Map.Entry<String, List<ConfigNumber>> sec : bySection.entrySet()) {
            String secKey = sec.getKey();
            String hintStr = hints.get(secKey);
            Component secTitle = Component.translatableWithFallback(
                    PREFIX + sheet.id() + ".section." + secKey, secKey);
            Component hintComp = hintStr == null ? null : Component.literal(hintStr);
            sections.add(new Section(secTitle, hintComp, List.of(new Group(null, sec.getValue()))));
        }
        return new Page(Component.translatable(PREFIX + "spell." + sheet.id()), sheet.color(), sections,
                worldEditable(spec), true, () -> WorldSettings.saveSpec(file, spec));
    }

    public static Page client() {
        ModConfigSpec spec = ClientSettings.SPEC;
        String file = ModConfigs.clientFile("client");
        Map<String, List<ConfigNumber>> sections = new LinkedHashMap<>();
        for (ClientSettings.Entry entry : ClientSettings.entries()) {
            ConfigNumber number = fromSpec(spec, file, "client", entry.key(), entry.value(), entry.unit(),
                    entry.step());
            if (entry.choices() > 0) {
                number = new ConfigNumber(number.label(), number.description(), number.unit(), number.min(),
                        number.max(), number.step(), number.whole(), number.defaultValue(), number.stored(),
                        number.store(), number.file(), number.path(), PREFIX + "client." + entry.key() + ".choice");
            }
            sections.computeIfAbsent(entry.section(), key -> new ArrayList<>()).add(number);
        }
        List<Section> list = new ArrayList<>();
        for (Map.Entry<String, List<ConfigNumber>> section : sections.entrySet()) {
            list.add(new Section(Component.translatable(PREFIX + "client." + section.getKey()), null,
                    List.of(new Group(null, section.getValue()))));
        }
        return new Page(Component.translatable(PREFIX + "client"), 0x8FD3FF, list, spec.isLoaded(), false,
                () -> WorldSettings.saveSpec(file, spec));
    }

    private static ConfigNumber fromSpec(ModConfigSpec spec, String file, String page, String key,
                                         ModConfigSpec.ConfigValue<? extends Number> value, Unit unit, double step) {
        ModConfigSpec.Range<?> range = value.getSpec().getRange();
        double min = range == null ? 0.0 : ((Number) range.getMin()).doubleValue();
        double max = range == null ? Double.MAX_VALUE : ((Number) range.getMax()).doubleValue();
        boolean whole = value instanceof ModConfigSpec.IntValue;
        String path = PREFIX + page + "." + key;
        return new ConfigNumber(Component.translatableWithFallback(path, key),
                Component.translatableWithFallback(path + ".desc", ""), unit, min, max, step, whole,
                value.getDefault().doubleValue(),
                () -> (spec.isLoaded() ? value.get() : value.getDefault()).doubleValue(),
                number -> set(value, number), file, value.getPath());
    }

    @SuppressWarnings("unchecked")
    private static void set(ModConfigSpec.ConfigValue<? extends Number> value, double number) {
        if (value instanceof ModConfigSpec.IntValue whole) {
            whole.set((int) Math.round(number));
        } else {
            ((ModConfigSpec.ConfigValue<Double>) value).set(number);
        }
    }
    // #endregion
}
