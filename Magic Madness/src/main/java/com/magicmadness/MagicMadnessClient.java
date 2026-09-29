package com.magicmadness;

// #region 1. IMPORTS
import com.magicmadness.config.ModConfigs;
import com.magicmadness.config.WorldSettings;
import com.magicmadness.config.client.ClientSettings;
import com.magicmadness.config.client.ConfigChoiceScreen;
import com.magicmadness.spell.client.ModKeybinds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
// #endregion

// ============================================================================
// MAGIC MADNESS — CLIENT ENTRYPOINT, KEYBINDS & CONFIG SCREEN REGISTRATION
// ============================================================================
@Mod(value = MagicMadness.MODID, dist = Dist.CLIENT)
public final class MagicMadnessClient {

    // #region 2. CLIENT INITIALIZATION
    public MagicMadnessClient(ModContainer container, IEventBus modEventBus) {
        ModConfigs.ensureFolders();
        String clientFile = ModConfigs.clientFile("client");
        WorldSettings.prepareFile(clientFile, ClientSettings.SPEC);
        modEventBus.addListener(ModKeybinds::register);
        container.registerConfig(ModConfig.Type.CLIENT, ClientSettings.SPEC, clientFile);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigChoiceScreen(parent));
    }
    // #endregion
}
