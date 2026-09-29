package com.magicmadness.spell;

// #region 1. IMPORTS
import com.magicmadness.config.PowerRules;
import com.magicmadness.config.SpellConfig;
import java.util.Locale;
// #endregion

// ============================================================================
// MAGIC MADNESS — ACTIVE SPELL REGISTRY (FIREBALL, LIGHTNING, WIND GUST)
// ============================================================================
public enum Spell {

    // #region 2. SPELL DEFINITIONS
    FIREBALL("fireball", "Fireball", MagicSchool.HEAT, 0xFF7A3D),
    LIGHTNING("lightning", "Lightning", MagicSchool.ELECTRIC, 0x62C6FF),
    WIND_GUST("wind_gust", "Wind Gust", MagicSchool.AIR, 0xA8F5E0);

    private final String id;
    private final String displayName;
    private final MagicSchool school;
    private final int accentColor;

    Spell(String id, String displayName, MagicSchool school, int accentColor) {
        this.id = id;
        this.displayName = displayName;
        this.school = school;
        this.accentColor = accentColor;
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public MagicSchool school() {
        return this.school;
    }

    public int accentColor() {
        return this.accentColor;
    }

    public String translationKey() {
        return "spell.magicmadness." + this.id;
    }

    public String descriptionKey() {
        return "spell.magicmadness." + this.id + ".desc";
    }

    public int cooldownTicks() {
        return switch (this) {
            case FIREBALL -> SpellConfig.get(SpellConfig.FIREBALL_COOLDOWN_TICKS);
            case LIGHTNING -> SpellConfig.get(SpellConfig.LIGHTNING_COOLDOWN_TICKS);
            case WIND_GUST -> SpellConfig.get(SpellConfig.WIND_GUST_COOLDOWN_TICKS);
        };
    }

    public double rangeBlocks() {
        return switch (this) {
            case FIREBALL -> SpellConfig.get(SpellConfig.FIREBALL_RANGE_BLOCKS) * PowerRules.range();
            case LIGHTNING -> SpellConfig.get(SpellConfig.LIGHTNING_RANGE_BLOCKS) * PowerRules.range();
            case WIND_GUST -> SpellConfig.get(SpellConfig.WIND_GUST_RANGE_BLOCKS) * PowerRules.range();
        };
    }

    public double damage() {
        return switch (this) {
            case FIREBALL -> (SpellConfig.get(SpellConfig.FIREBALL_DIRECT_DAMAGE)
                    + SpellConfig.get(SpellConfig.FIREBALL_SPLASH_DAMAGE)) * PowerRules.damage();
            case LIGHTNING -> (SpellConfig.get(SpellConfig.LIGHTNING_STRIKE_DAMAGE)
                    + SpellConfig.get(SpellConfig.LIGHTNING_SHOCK_DAMAGE)) * PowerRules.damage();
            case WIND_GUST -> SpellConfig.get(SpellConfig.WIND_GUST_DAMAGE) * PowerRules.damage();
        };
    }

    public static Spell byId(String id) {
        if (id == null) {
            return null;
        }
        String clean = id.trim().toLowerCase(Locale.ROOT);
        for (Spell spell : values()) {
            if (spell.id.equals(clean)) {
                return spell;
            }
        }
        return null;
    }
    // #endregion
}
