package com.magicmadness.config;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import java.math.BigDecimal;
import net.minecraft.network.chat.Component;
// #endregion

// ============================================================================
// MAGIC MADNESS — SPELL CONFIG VALUE DISPLAY UNITS
// ============================================================================
public enum Unit {

    // #region 2. UNIT CONSTANTS
    HALF_HEARTS,
    TICKS,
    SECONDS,
    BLOCKS,
    BLOCKS_PER_TICK,
    BLOCKS_PER_SECOND,
    DEGREES,
    HARDNESS,
    COUNT,
    STRENGTH,
    SWITCH,
    PERCENT,
    CHOICE;

    private static final String PREFIX = "config." + MagicMadness.MODID + ".unit.";
    // #endregion

    // #region 3. HUMAN-READABLE FORMATTING
    public Component describe(double value) {
        return switch (this) {
            case HALF_HEARTS -> value <= 0.0 ? key("no_damage") : key("hearts", number(value / 2.0));
            case TICKS -> value <= 0.0 ? key("instant") : key("seconds", number(value / 20.0));
            case SECONDS -> key("seconds", number(value));
            case BLOCKS -> key("blocks", number(value));
            case BLOCKS_PER_TICK -> key("blocks_per_second", number(value * 20.0));
            case BLOCKS_PER_SECOND -> key("blocks_per_second", number(value));
            case DEGREES -> key("degrees", number(value));
            case HARDNESS -> value < 0.0 ? key("breaks_nothing") : key("breaks_up_to", key(material(value)));
            case COUNT -> key("count", number(value));
            case STRENGTH -> key("factor", number(value));
            case SWITCH -> key(value >= 0.5 ? "on" : "off");
            case PERCENT -> key("percent", number(Math.round(value * 100.0)));
            case CHOICE -> key("count", number(value));
        };
    }

    private static String material(double hardness) {
        if (hardness >= 50.0) return "material.obsidian";
        if (hardness >= 5.0) return "material.iron";
        if (hardness >= 3.0) return "material.ores";
        if (hardness >= 2.0) return "material.wood";
        if (hardness >= 1.5) return "material.stone";
        return hardness >= 0.5 ? "material.dirt" : "material.leaves";
    }

    private static Component key(String name, Object... args) {
        return Component.translatable(PREFIX + name, args);
    }

    public static String number(double value) {
        return BigDecimal.valueOf(Math.round(value * 100.0) / 100.0).stripTrailingZeros().toPlainString();
    }
    // #endregion
}
