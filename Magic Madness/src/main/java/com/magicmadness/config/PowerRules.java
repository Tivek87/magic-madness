package com.magicmadness.config;

// #region 1. IMPORTS
import com.mojang.authlib.GameProfile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
// #endregion

// ============================================================================
// MAGIC MADNESS — GLOBAL SERVER SPELL RULES & OWNER ACCESS CONTROL
// ============================================================================
public final class PowerRules {

    // #region 2. CONFIG SPEC & VALUES
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue DAMAGE;
    public static final ModConfigSpec.DoubleValue COOLDOWNS;
    public static final ModConfigSpec.DoubleValue KNOCKBACK;
    public static final ModConfigSpec.DoubleValue RANGE;
    public static final ModConfigSpec.DoubleValue RADIUS;
    public static final ModConfigSpec.IntValue BREAK_BLOCKS;
    public static final ModConfigSpec.DoubleValue MAX_BLOCK_HARDNESS;
    public static final ModConfigSpec.IntValue FRIENDLY_FIRE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> OWNERS;

    public static final List<String> SPELL_IDS = List.of("fireball", "lightning", "wind_gust");
    private static final Map<String, ModConfigSpec.IntValue> ALLOWED_SPELLS = new LinkedHashMap<>();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(ModConfigs.WIP,
                "Global rules for every spell in this world.",
                "Saved in config/magic_madness/server/general.toml.",
                "Only the world host or a listed server owner may open and change these in-game: Mods > Magic Madness > Config > Server.")
                .push("spells");
        DAMAGE = builder.comment("Multiplies the damage of every spell (1 = default, 0 = no damage)")
                .defineInRange("damageMultiplier", 1.0, 0.0, 10.0);
        COOLDOWNS = builder.comment("Multiplies the cooldown of every spell (1 = default, 0 = no cooldowns)")
                .defineInRange("cooldownMultiplier", 1.0, 0.0, 10.0);
        KNOCKBACK = builder.comment("Multiplies knockback & push force of spells (1 = default, 0 = none)")
                .defineInRange("knockbackMultiplier", 1.0, 0.0, 5.0);
        RANGE = builder.comment("Multiplies projectile & arc range of spells (1 = default)")
                .defineInRange("rangeMultiplier", 1.0, 0.2, 5.0);
        RADIUS = builder.comment("Multiplies AoE blast radius of spells (1 = default)")
                .defineInRange("radiusMultiplier", 1.0, 0.2, 5.0);
        BREAK_BLOCKS = builder.comment("Whether spells may break blocks (1 = yes, 0 = never)")
                .defineInRange("breakBlocks", 0, 0, 1);
        MAX_BLOCK_HARDNESS = builder.comment("Hardest block hardness spells may break (-1 = nothing, 50 = obsidian)")
                .defineInRange("maxBlockHardness", 5.0, -1.0, 50.0);
        FRIENDLY_FIRE = builder.comment("Whether area spells can hit allied players (1 = yes, 0 = no)")
                .defineInRange("friendlyFire", 0, 0, 1);
        builder.pop();

        builder.comment("Which spells are enabled and can be cast in this world.")
                .push("allowed_spells");
        for (String id : SPELL_IDS) {
            ALLOWED_SPELLS.put(id, builder.comment("Whether " + id + " can be cast (1 = yes, 0 = no)")
                    .defineInRange(id, 1, 0, 1));
        }
        builder.pop();

        builder.comment("Who may open and change the server spell settings in the game (Mods > Magic Madness > Config > Server).",
                "In singleplayer and on a LAN world the host always may; on a dedicated server only listed owners may.")
                .push("access");
        OWNERS = builder.comment("The owners of this server by player name or UUID, e.g. [\"Tivek\", \"Steve\"]."
                + " Only they (and the singleplayer/LAN host) may open the Server Config in-game.")
                .defineListAllowEmpty("owners", List.of(), () -> "", PowerRules::validOwner);
        builder.pop();
        SPEC = builder.build();
    }

    private PowerRules() {}
    // #endregion

    // #region 3. ACCESSORS & OWNER CHECK
    public static Map<String, ModConfigSpec.IntValue> allowedSpells() {
        return Collections.unmodifiableMap(ALLOWED_SPELLS);
    }

    public static boolean isSpellAllowed(String id) {
        ModConfigSpec.IntValue val = ALLOWED_SPELLS.get(id);
        if (val == null) return true;
        return (SPEC.isLoaded() ? val.get() : val.getDefault()) >= 1;
    }

    public static double damage() {
        return SPEC.isLoaded() ? DAMAGE.get() : DAMAGE.getDefault();
    }

    public static double cooldowns() {
        return SPEC.isLoaded() ? COOLDOWNS.get() : COOLDOWNS.getDefault();
    }

    public static double knockback() {
        return SPEC.isLoaded() ? KNOCKBACK.get() : KNOCKBACK.getDefault();
    }

    public static double range() {
        return SPEC.isLoaded() ? RANGE.get() : RANGE.getDefault();
    }

    public static double radius() {
        return SPEC.isLoaded() ? RADIUS.get() : RADIUS.getDefault();
    }

    public static boolean friendlyFire() {
        return (SPEC.isLoaded() ? FRIENDLY_FIRE.get() : FRIENDLY_FIRE.getDefault()) >= 1;
    }

    public static boolean isOwner(GameProfile profile) {
        if (!SPEC.isLoaded() || profile == null) {
            return false;
        }
        String id = profile.getId() == null ? "" : profile.getId().toString();
        for (String owner : OWNERS.get()) {
            String name = owner.trim();
            if (!name.isEmpty() && (name.equalsIgnoreCase(profile.getName()) || name.equalsIgnoreCase(id))) {
                return true;
            }
        }
        return false;
    }

    private static boolean validOwner(Object owner) {
        return owner instanceof String name && name.length() <= 36;
    }
    // #endregion
}
