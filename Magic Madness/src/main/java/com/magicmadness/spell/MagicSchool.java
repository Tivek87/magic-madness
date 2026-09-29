package com.magicmadness.spell;

// ============================================================================
// MAGIC MADNESS — ELEMENTAL MAGIC SCHOOLS (HEAT, AIR, NATURE, ELECTRIC, VOID, HYDRO)
// ============================================================================
public enum MagicSchool {

    // #region 1. SCHOOLS, THEMES & ACCENT COLORS
    HEAT("Heat", "Fire, heat, lava", 0xFF7A3D),
    AIR("Air", "Wind, air", 0xA8F5E0),
    NATURE("Nature", "Plants, earth, fungi, poison", 0x5CE65C),
    ELECTRIC("Electric", "Lightning, electricity", 0x62C6FF),
    VOID("Void", "Void, darkness", 0xC07CFF),
    HYDRO("Hydro", "Water, ice, snow", 0x3898FF);

    private final String displayName;
    private final String elements;
    private final int rgb;

    MagicSchool(String displayName, String elements, int rgb) {
        this.displayName = displayName;
        this.elements = elements;
        this.rgb = rgb;
    }

    public String displayName() {
        return this.displayName;
    }

    public String elements() {
        return this.elements;
    }

    public int rgb() {
        return this.rgb;
    }
    // #endregion
}
