package com.magicmadness.config;

// #region 1. IMPORTS
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
// #endregion

// ============================================================================
// MAGIC MADNESS — CENTRAL SPELL CONFIG REGISTRY (config/magic_madness/...)
// ============================================================================
public final class ModConfigs {

    // #region 2. CONSTANTS & STORAGE
    public static final String ROOT_FOLDER = "magic_madness";
    public static final String CLIENT_FOLDER = ROOT_FOLDER + "/client";
    public static final String SERVER_FOLDER = ROOT_FOLDER + "/server";
    public static final String WIP = "!!! ALPHA / WORK IN PROGRESS !!! Magic Madness is in active development:"
            + " spell settings may be added, tuned or expanded.";

    private static final Map<String, ModConfigSpec> SERVER_FILES = new LinkedHashMap<>();

    private ModConfigs() {}
    // #endregion

    // #region 3. REGISTRATION & FILE HELPERS
    public static void ensureFolders() {
        try {
            Path cfg = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
            Files.createDirectories(cfg.resolve(CLIENT_FOLDER));
            Files.createDirectories(cfg.resolve(SERVER_FOLDER));
        } catch (IOException ignored) {
        }
    }

    public static void register(ModContainer container, IEventBus modEventBus) {
        ensureFolders();
        server(container, "general", PowerRules.SPEC);
        SpellConfig.register(container);
        WorldSettings.ensureLocalDefaultsLoaded();
        modEventBus.addListener(ModConfigEvent.Reloading.class, WorldSettings::onReload);
    }

    public static void server(ModContainer container, String name, ModConfigSpec spec) {
        String file = serverFile(name);
        SERVER_FILES.put(file, spec);
        container.registerConfig(ModConfig.Type.COMMON, spec, file);
    }

    public static Map<String, ModConfigSpec> worldFiles() {
        return Collections.unmodifiableMap(SERVER_FILES);
    }

    public static String clientFile(String name) {
        return CLIENT_FOLDER + "/" + name + ".toml";
    }

    public static String serverFile(String name) {
        return SERVER_FOLDER + "/" + name + ".toml";
    }
    // #endregion
}
