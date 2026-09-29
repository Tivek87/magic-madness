package com.magicmadness.config.client;

// #region 1. IMPORTS
import com.magicmadness.config.ModConfigs;
import com.magicmadness.config.Unit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
// #endregion

// ============================================================================
// MAGIC MADNESS — CLIENT SPELL SETTINGS (SPELL VIEW, 3D SPELL VFX & SPELL SOUND)
// ============================================================================
public final class ClientSettings {

    // #region 2. CONFIG SPEC & FIELDS
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    public static final ModConfigSpec.DoubleValue SCREEN_FLASH;

    public static final ModConfigSpec.DoubleValue PARTICLE_AMOUNT;
    public static final ModConfigSpec.IntValue EFFECT_DETAIL;
    public static final ModConfigSpec.IntValue ASH_DISINTEGRATION;
    public static final ModConfigSpec.DoubleValue HUD_OPACITY;
    public static final ModConfigSpec.IntValue SHOW_COOLDOWN_NUMBERS;
    public static final ModConfigSpec.IntValue KILL_CONFIRM;

    public static final ModConfigSpec.DoubleValue SPELL_VOLUME;

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(ModConfigs.WIP,
                "Your personal spell client settings: spell camera feedback, 3D spell visuals, radial HUD and spell audio.",
                "Saved in config/magic_madness/client/client.toml. Customizable in-game via Mods > Magic Madness > Config > Client.")
                .push("view");
        Sheet sheet = new Sheet(builder, "view");
        CAMERA_SHAKE = sheet.number("cameraShake",
                "How hard your view shakes from Fireball and spell explosions (1 = default, 0 = never)",
                1.0, 0.0, 2.0, Unit.STRENGTH, 0.1);
        SCREEN_FLASH = sheet.number("screenFlash",
                "How bright the screen flashes on Fireball impacts (1 = default, 0 = never)",
                1.0, 0.0, 2.0, Unit.PERCENT, 0.05);

        sheet.section("effects");
        PARTICLE_AMOUNT = sheet.number("particleAmount",
                "Multiplier for particles spawned by spells (1 = default, 0 = none)",
                1.0, 0.0, 2.0, Unit.PERCENT, 0.05);
        EFFECT_DETAIL = sheet.choice("effectDetail",
                "3D geometry detail for custom spell VFX, flame ribbons and fire cores (0 = low, 1 = medium, 2 = full)",
                2, 3);
        ASH_DISINTEGRATION = sheet.toggle("ashDisintegration",
                "Disintegrate enemies into glowing ash particles when defeated by Fireball",
                true);
        HUD_OPACITY = sheet.number("hudOpacity",
                "Opacity of the in-game Spell Radial Menu and cooldown HUD",
                0.95, 0.2, 1.0, Unit.PERCENT, 0.05);
        SHOW_COOLDOWN_NUMBERS = sheet.toggle("showCooldownNumbers",
                "Display exact remaining cooldown seconds on the radial menu and HUD",
                true);
        KILL_CONFIRM = sheet.toggle("killConfirm",
                "Show red crosshair flick when you defeat an enemy with a spell",
                true);

        sheet.section("sound");
        SPELL_VOLUME = sheet.number("spellVolume",
                "Volume multiplier for spell casting, flight and explosion sounds",
                1.0, 0.0, 2.0, Unit.PERCENT, 0.05);
        builder.pop();
        SPEC = builder.build();
    }
    // #endregion

    // #region 3. ENTRY RECORD, SHEET BUILDER & GETTERS
    public record Entry(String section, String key, ModConfigSpec.ConfigValue<? extends Number> value,
                        Unit unit, double step, int choices) {}

    public static List<Entry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    private static final class Sheet {
        private final ModConfigSpec.Builder builder;
        private String section;

        Sheet(ModConfigSpec.Builder builder, String section) {
            this.builder = builder;
            this.section = section;
        }

        void section(String name) {
            this.builder.pop();
            this.builder.push(name);
            this.section = name;
        }

        ModConfigSpec.DoubleValue number(String key, String comment, double val, double min, double max,
                                         Unit unit, double step) {
            ModConfigSpec.DoubleValue defined = this.builder.comment(comment).defineInRange(key, val, min, max);
            ENTRIES.add(new Entry(this.section, key, defined, unit, step, 0));
            return defined;
        }

        ModConfigSpec.IntValue whole(String key, String comment, int val, int min, int max,
                                     Unit unit, double step) {
            ModConfigSpec.IntValue defined = this.builder.comment(comment).defineInRange(key, val, min, max);
            ENTRIES.add(new Entry(this.section, key, defined, unit, step, 0));
            return defined;
        }

        ModConfigSpec.IntValue toggle(String key, String comment, boolean on) {
            return this.whole(key, comment, on ? 1 : 0, 0, 1, Unit.SWITCH, 1.0);
        }

        ModConfigSpec.IntValue choice(String key, String comment, int val, int count) {
            ModConfigSpec.IntValue defined = this.builder.comment(comment).defineInRange(key, val, 0, count - 1);
            ENTRIES.add(new Entry(this.section, key, defined, Unit.CHOICE, 1.0, count));
            return defined;
        }
    }

    private ClientSettings() {}

    public static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
    // #endregion
}
