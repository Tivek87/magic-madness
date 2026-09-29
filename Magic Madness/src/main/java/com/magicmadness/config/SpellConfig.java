package com.magicmadness.config;

// #region 1. IMPORTS
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;
// #endregion

// ============================================================================
// MAGIC MADNESS — PER-SPELL SERVER CONFIG (FIREBALL, LIGHTNING, WIND GUST)
// ============================================================================
public final class SpellConfig {

    // #region 2. RECORDS & SPELL CONFIG FIELDS
    public record Entry(String section, String hint, String key,
                        ModConfigSpec.ConfigValue<? extends Number> value,
                        Unit unit, double step) {}

    public record SpellSheet(String id, int color, ModConfigSpec spec, List<Entry> entries) {}

    private static final Map<String, SpellSheet> SHEETS = new LinkedHashMap<>();

    // Fireball (Heat School — 10 Damage)
    public static ModConfigSpec.DoubleValue FIREBALL_DIRECT_DAMAGE;
    public static ModConfigSpec.DoubleValue FIREBALL_SPLASH_DAMAGE;
    public static ModConfigSpec.DoubleValue FIREBALL_BLAST_RADIUS;
    public static ModConfigSpec.DoubleValue FIREBALL_BURN_SECONDS;
    public static ModConfigSpec.IntValue FIREBALL_IGNITE_BLOCKS;
    public static ModConfigSpec.IntValue FIREBALL_COOLDOWN_TICKS;
    public static ModConfigSpec.DoubleValue FIREBALL_RANGE_BLOCKS;
    public static ModConfigSpec.DoubleValue FIREBALL_GRAVITY;
    public static ModConfigSpec.IntValue FIREBALL_WATER_EXTINGUISH;

    // Lightning (Electric School — Stronger than Fireball: 16 Damage)
    public static ModConfigSpec.DoubleValue LIGHTNING_STRIKE_DAMAGE;
    public static ModConfigSpec.DoubleValue LIGHTNING_SHOCK_DAMAGE;
    public static ModConfigSpec.DoubleValue LIGHTNING_SHOCK_RADIUS;
    public static ModConfigSpec.DoubleValue LIGHTNING_KNOCKBACK_STRENGTH;
    public static ModConfigSpec.DoubleValue LIGHTNING_KNOCKBACK_LIFT;
    public static ModConfigSpec.DoubleValue LIGHTNING_CHAIN_DAMAGE;
    public static ModConfigSpec.DoubleValue LIGHTNING_CHAIN_RANGE;
    public static ModConfigSpec.IntValue LIGHTNING_MAX_CHAINS_CLEAR;
    public static ModConfigSpec.IntValue LIGHTNING_MAX_CHAINS_RAIN;
    public static ModConfigSpec.IntValue LIGHTNING_CHARGE_TICKS;
    public static ModConfigSpec.IntValue LIGHTNING_COOLDOWN_TICKS;
    public static ModConfigSpec.DoubleValue LIGHTNING_RANGE_BLOCKS;

    // Wind Gust (Air School — Low Damage: 3 Damage, Massive Knockback)
    public static ModConfigSpec.DoubleValue WIND_GUST_DAMAGE;
    public static ModConfigSpec.DoubleValue WIND_GUST_PUSH_STRENGTH;
    public static ModConfigSpec.DoubleValue WIND_GUST_LIFT_STRENGTH;
    public static ModConfigSpec.DoubleValue WIND_GUST_RECOIL_BOOST;
    public static ModConfigSpec.IntValue WIND_GUST_REFLECT_PROJECTILES;
    public static ModConfigSpec.IntValue WIND_GUST_COOLDOWN_TICKS;
    public static ModConfigSpec.DoubleValue WIND_GUST_RANGE_BLOCKS;
    public static ModConfigSpec.DoubleValue WIND_GUST_CONE_DEGREES;

    static {
        buildFireball();
        buildLightning();
        buildWindGust();
    }

    private SpellConfig() {}
    // #endregion

    // #region 3. BUILD FIREBALL, LIGHTNING & WIND GUST SHEETS
    private static void buildFireball() {
        Builder b = new Builder("fireball", 0xFF7A3D);
        b.section("damage_and_burn", "Impact, Explosion & Fire");
        FIREBALL_DIRECT_DAMAGE = b.number("directDamage", "Direct hit damage in half hearts", 6.0, 0.0, 200.0, Unit.HALF_HEARTS, 1.0);
        FIREBALL_SPLASH_DAMAGE = b.number("splashDamage", "Explosion AoE splash damage in half hearts", 4.0, 0.0, 200.0, Unit.HALF_HEARTS, 1.0);
        FIREBALL_BLAST_RADIUS = b.number("blastRadiusBlocks", "Explosion AoE radius in blocks", 2.5, 0.5, 16.0, Unit.BLOCKS, 0.5);
        FIREBALL_BURN_SECONDS = b.number("burnSeconds", "Target burn duration in seconds", 4.0, 0.0, 30.0, Unit.SECONDS, 0.5);
        FIREBALL_IGNITE_BLOCKS = b.whole("igniteBlocks", "Whether explosion ignites a 3x3 area (1 = yes, 0 = no)", 1, 0, 1, Unit.SWITCH, 1.0);

        b.section("cooldown_and_arc", "Cooldown, 10m Range & Arc Weight");
        FIREBALL_COOLDOWN_TICKS = b.whole("cooldownTicks", "Cooldown in ticks (100 ticks = 5.0 seconds)", 100, 0, 6000, Unit.TICKS, 10.0);
        FIREBALL_RANGE_BLOCKS = b.number("rangeBlocks", "Weighted arc range in blocks before hitting the ground (default: 10.0)", 10.0, 2.0, 64.0, Unit.BLOCKS, 0.5);
        FIREBALL_GRAVITY = b.number("arcGravity", "Downward pull (weight) curving the Fireball downward in an arc", 0.055, 0.0, 0.3, Unit.STRENGTH, 0.005);
        FIREBALL_WATER_EXTINGUISH = b.whole("waterExtinguishesFire", "Whether water extinguishes Fireball into steam (1 = yes, 0 = no)", 1, 0, 1, Unit.SWITCH, 1.0);
        b.finish();
    }

    private static void buildLightning() {
        Builder b = new Builder("lightning", 0x62C6FF);
        b.section("strike_and_chain", "Sky Bolt Strike, AoE Shockwave & Chain Lightning");
        LIGHTNING_STRIKE_DAMAGE = b.number("strikeDamage", "Direct lightning bolt strike damage in half hearts (default: 10.0)", 10.0, 0.0, 200.0, Unit.HALF_HEARTS, 1.0);
        LIGHTNING_SHOCK_DAMAGE = b.number("shockDamage", "Ground shockwave AoE damage in half hearts (default: 6.0)", 6.0, 0.0, 200.0, Unit.HALF_HEARTS, 1.0);
        LIGHTNING_SHOCK_RADIUS = b.number("shockRadiusBlocks", "Ground electric shockwave radius in blocks", 3.5, 0.5, 16.0, Unit.BLOCKS, 0.5);
        LIGHTNING_KNOCKBACK_STRENGTH = b.number("knockbackStrength", "Horizontal electric shockwave knockback force", 0.50, 0.0, 6.0, Unit.STRENGTH, 0.1);
        LIGHTNING_KNOCKBACK_LIFT = b.number("knockbackLift", "Upward electric jolt force", 0.30, 0.0, 3.0, Unit.STRENGTH, 0.05);
        LIGHTNING_CHAIN_DAMAGE = b.number("chainDamage", "Chain lightning arc damage per jump in half hearts", 5.0, 0.0, 200.0, Unit.HALF_HEARTS, 1.0);
        LIGHTNING_CHAIN_RANGE = b.number("chainRangeBlocks", "Max jump distance in blocks between chained targets", 6.5, 1.0, 24.0, Unit.BLOCKS, 0.5);
        LIGHTNING_MAX_CHAINS_CLEAR = b.whole("maxChainsClear", "Max chain lightning jumps in clear weather", 3, 0, 12, Unit.COUNT, 1.0);
        LIGHTNING_MAX_CHAINS_RAIN = b.whole("maxChainsRain", "Max chain lightning jumps in rain or thunderstorm", 5, 0, 16, Unit.COUNT, 1.0);

        b.section("cooldown_and_range", "Cooldown, 24m Range & Sky-Rune Charge");
        LIGHTNING_COOLDOWN_TICKS = b.whole("cooldownTicks", "Cooldown in ticks (140 ticks = 7.0 seconds)", 140, 0, 6000, Unit.TICKS, 10.0);
        LIGHTNING_RANGE_BLOCKS = b.number("rangeBlocks", "Max target lock range in blocks (default: 24.0)", 24.0, 4.0, 96.0, Unit.BLOCKS, 1.0);
        LIGHTNING_CHARGE_TICKS = b.whole("chargeTicks", "Sky-rune target lock charge ticks before bolt strikes (8 ticks = 0.4s)", 8, 0, 40, Unit.TICKS, 1.0);
        b.finish();
    }

    private static void buildWindGust() {
        Builder b = new Builder("wind_gust", 0xA8F5E0);
        b.section("knockback_and_gale", "Low Damage, Massive Gale Knockback & Projectile Reflection");
        WIND_GUST_DAMAGE = b.number("gustDamage", "Wind Gust impact damage in half hearts (default: 3.0 = 1.5 hearts)", 3.0, 0.0, 200.0, Unit.HALF_HEARTS, 0.5);
        WIND_GUST_PUSH_STRENGTH = b.number("pushStrength", "Massive horizontal gale knockback force", 2.25, 0.0, 8.0, Unit.STRENGTH, 0.1);
        WIND_GUST_LIFT_STRENGTH = b.number("liftStrength", "Upward airborne launch force on hit enemies", 0.78, 0.0, 4.0, Unit.STRENGTH, 0.05);
        WIND_GUST_RECOIL_BOOST = b.number("recoilBoost", "Self air-jump / fall-break boost when casting downward in mid-air", 0.68, 0.0, 3.0, Unit.STRENGTH, 0.05);
        WIND_GUST_REFLECT_PROJECTILES = b.whole("reflectProjectiles", "Whether Wind Gust reflects incoming arrows and projectiles (1 = yes, 0 = no)", 1, 0, 1, Unit.SWITCH, 1.0);

        b.section("cooldown_and_cone", "Cooldown, 10m Range & Storm Cone Angle");
        WIND_GUST_COOLDOWN_TICKS = b.whole("cooldownTicks", "Cooldown in ticks (70 ticks = 3.5 seconds)", 70, 0, 6000, Unit.TICKS, 10.0);
        WIND_GUST_RANGE_BLOCKS = b.number("rangeBlocks", "Storm cone reach in blocks (default: 10.0)", 10.0, 2.0, 48.0, Unit.BLOCKS, 0.5);
        WIND_GUST_CONE_DEGREES = b.number("coneHalfAngleDegrees", "Half-angle of the storm cone in degrees (default: 38.0)", 38.0, 10.0, 85.0, Unit.DEGREES, 2.0);
        b.finish();
    }

    public static double get(ModConfigSpec.DoubleValue val) {
        try {
            return val.get();
        } catch (RuntimeException e) {
            return val.getDefault();
        }
    }

    public static int get(ModConfigSpec.IntValue val) {
        try {
            return val.get();
        } catch (RuntimeException e) {
            return val.getDefault();
        }
    }
    // #endregion

    // #region 4. BUILDER & PUBLIC ACCESSORS
    private static final class Builder {
        private final String id;
        private final int color;
        private final ModConfigSpec.Builder spec = new ModConfigSpec.Builder();
        private final List<Entry> entries = new ArrayList<>();
        private String section;
        private String hint;

        Builder(String id, int color) {
            this.id = id;
            this.color = color;
            this.spec.comment(ModConfigs.WIP, "Server balance settings for the " + id + " spell.").push("spell");
        }

        void section(String name, String sectionHint) {
            if (this.section != null) {
                this.spec.pop();
            }
            this.section = name;
            this.hint = sectionHint;
            this.spec.push(name);
        }

        ModConfigSpec.DoubleValue number(String key, String comment, double def, double min, double max, Unit unit, double step) {
            ModConfigSpec.DoubleValue val = this.spec.comment(comment).defineInRange(key, def, min, max);
            this.entries.add(new Entry(this.section, this.hint, key, val, unit, step));
            return val;
        }

        ModConfigSpec.IntValue whole(String key, String comment, int def, int min, int max, Unit unit, double step) {
            ModConfigSpec.IntValue val = this.spec.comment(comment).defineInRange(key, def, min, max);
            this.entries.add(new Entry(this.section, this.hint, key, val, unit, step));
            return val;
        }

        void finish() {
            if (this.section != null) {
                this.spec.pop();
            }
            this.spec.pop();
            SHEETS.put(this.id, new SpellSheet(this.id, this.color, this.spec.build(),
                    Collections.unmodifiableList(this.entries)));
        }
    }

    public static void register(ModContainer container) {
        for (SpellSheet sheet : SHEETS.values()) {
            ModConfigs.server(container, sheet.id(), sheet.spec());
        }
    }

    public static Map<String, SpellSheet> sheets() {
        return Collections.unmodifiableMap(SHEETS);
    }
    // #endregion
}
