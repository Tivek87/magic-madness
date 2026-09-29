package com.magicmadness.spell.client;

// #region 1. IMPORTS
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
// #endregion

// ============================================================================
// MAGIC MADNESS — KEYBINDS (CATEGORY: MAGIC MADNESS, KEY: G HOLD RADIAL MENU)
// ============================================================================
public final class ModKeybinds {

    // #region 2. KEY MAPPING DEFINITION & REGISTRATION
    public static final String CATEGORY = "key.categories.magicmadness";

    public static final KeyMapping SPELL_RADIAL = new KeyMapping(
            "key.magicmadness.spell_radial",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    private ModKeybinds() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SPELL_RADIAL);
    }
    // #endregion
}
