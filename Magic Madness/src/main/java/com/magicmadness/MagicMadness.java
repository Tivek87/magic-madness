package com.magicmadness;

// #region 1. IMPORTS
import com.magicmadness.config.ModConfigs;
import com.magicmadness.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
// #endregion

// ============================================================================
// MAGIC MADNESS — CORE MOD ENTRYPOINT (NEOFORGE 1.21.1)
// ============================================================================
@Mod(MagicMadness.MODID)
public final class MagicMadness {

    // #region 2. CONSTANTS & LOGGER
    public static final String MODID = "magicmadness";
    public static final String MOD_NAME = "Magic Madness";
    public static final Logger LOGGER = LogUtils.getLogger();
    // #endregion

    // #region 3. INITIALIZATION
    public MagicMadness(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(ModNetwork::register);
        ModConfigs.register(modContainer, modEventBus);
        LOGGER.info("{} ({}) initialized.", MOD_NAME, MODID);
    }
    // #endregion
}
