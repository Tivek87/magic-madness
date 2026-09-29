package com.magicmadness.config;

// #region 1. IMPORTS
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.magicmadness.MagicMadness;
import com.magicmadness.network.WorldSettingsEditPayload;
import com.magicmadness.network.WorldSettingsPayload;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
// #endregion

// ============================================================================
// MAGIC MADNESS — CONFIG STORAGE (config/magic_madness/client & server) & SYNC
// ============================================================================
public final class WorldSettings {

    // #region 2. CONSTANTS
    private WorldSettings() {}
    // #endregion

    // #region 3. CONFIG PREPARATION & DISK SAVING IN config/magic_madness/
    public static void prepare(MinecraftServer server) {
        ensureLocalDefaultsLoaded();
    }

    public static void prepareFile(String file, ModConfigSpec spec) {
        try {
            ensureConfigFile(file, spec);
        } catch (IOException | RuntimeException e) {
            MagicMadness.LOGGER.warn("Could not prepare config {}", file, e);
        }
    }

    public static void ensureLocalDefaultsLoaded() {
        Path configRoot = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        Path serverDir = configRoot.resolve(ModConfigs.SERVER_FOLDER);

        // Clean obsolete .toml files in config/magic_madness/server/
        if (Files.isDirectory(serverDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(serverDir, "*.toml")) {
                for (Path existing : stream) {
                    String relKey = ModConfigs.SERVER_FOLDER + "/" + existing.getFileName().toString();
                    if (!ModConfigs.worldFiles().containsKey(relKey)) {
                        Files.deleteIfExists(existing);
                    }
                }
            } catch (IOException e) {
                MagicMadness.LOGGER.warn("Could not clean obsolete configs in {}", serverDir, e);
            }
        }

        // Ensure all active server configs exist and are up-to-date in config/magic_madness/server/
        for (Map.Entry<String, ModConfigSpec> entry : ModConfigs.worldFiles().entrySet()) {
            try {
                ensureConfigFile(entry.getKey(), entry.getValue());
            } catch (IOException | RuntimeException e) {
                MagicMadness.LOGGER.warn("Could not prepare server config {}", entry.getKey(), e);
            }
        }
    }

    public static void saveSpec(String file, ModConfigSpec spec) {
        if (!spec.isLoaded()) {
            return;
        }
        Path target = FMLPaths.CONFIGDIR.get().resolve(file).toAbsolutePath().normalize();
        try {
            Files.createDirectories(target.getParent());
            CommentedConfig saved = ordered();
            spec.correct(saved);
            copyValues(saved, spec.getValues());
            spec.correct(saved);
            write(target, saved);
        } catch (IOException | RuntimeException e) {
            MagicMadness.LOGGER.warn("Could not save config {}", file, e);
        }
    }

    public static void applySyncedBytes(String file, byte[] contents) {
        ModConfigSpec spec = ModConfigs.worldFiles().get(file);
        if (spec == null || !spec.isLoaded()) {
            return;
        }
        CommentedConfig parsed = ordered();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(contents), StandardCharsets.UTF_8)) {
            TomlFormat.instance().createParser().parse(reader, parsed, ParsingMode.REPLACE);
            applyConfigToSpec(parsed, spec.getValues());
        } catch (IOException | RuntimeException e) {
            MagicMadness.LOGGER.warn("Could not apply synced server config {}", file, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyConfigToSpec(Config from, UnmodifiableConfig specValues) {
        for (UnmodifiableConfig.Entry entry : specValues.entrySet()) {
            Object raw = entry.getRawValue();
            Object incoming = from.getRaw(List.of(entry.getKey()));
            if (raw instanceof UnmodifiableConfig subSpec && incoming instanceof Config subFrom) {
                applyConfigToSpec(subFrom, subSpec);
            } else if (raw instanceof ModConfigSpec.ConfigValue cv && incoming != null) {
                try {
                    cv.set(incoming);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private static void copyValues(Config into, UnmodifiableConfig values) {
        for (Config.Entry entry : into.entrySet()) {
            Object raw = values.getRaw(List.of(entry.getKey()));
            if (entry.getRawValue() instanceof Config section && raw instanceof UnmodifiableConfig sub) {
                copyValues(section, sub);
            } else if (raw instanceof ModConfigSpec.ConfigValue<?> cv) {
                entry.setValue(cv.get());
            }
        }
    }

    private static Path ensureConfigFile(String file, ModConfigSpec spec) throws IOException {
        Path target = FMLPaths.CONFIGDIR.get().resolve(file).toAbsolutePath().normalize();
        if (!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            write(target, rebuilt(spec, ordered()));
            return target;
        }
        CommentedConfig old = ordered();
        try (Reader reader = Files.newBufferedReader(target)) {
            TomlFormat.instance().createParser().parse(reader, old, ParsingMode.REPLACE);
        } catch (RuntimeException e) {
            write(target, rebuilt(spec, ordered()));
            return target;
        }
        CommentedConfig now = rebuilt(spec, old);
        String expected = TomlFormat.instance().createWriter().writeToString(now);
        if (!expected.equals(Files.readString(target))) {
            write(target, now);
        }
        return target;
    }

    private static CommentedConfig rebuilt(ModConfigSpec spec, UnmodifiableConfig old) {
        CommentedConfig fresh = ordered();
        spec.correct(fresh);
        keep(fresh, old);
        spec.correct(fresh);
        return fresh;
    }

    private static void keep(Config into, UnmodifiableConfig from) {
        for (Config.Entry entry : into.entrySet()) {
            Object had = from.getRaw(List.of(entry.getKey()));
            if (entry.getRawValue() instanceof Config section) {
                if (had instanceof UnmodifiableConfig oldSection) {
                    keep(section, oldSection);
                }
            } else if (had != null && !(had instanceof UnmodifiableConfig)) {
                entry.setValue(had);
            }
        }
    }

    private static CommentedConfig ordered() {
        return CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
    }

    private static void write(Path file, CommentedConfig config) {
        Path abs = file.toAbsolutePath().normalize();
        TomlFormat.instance().createWriter().write(config, abs, WritingMode.REPLACE);
    }
    // #endregion

    // #region 4. OWNER PERMISSION CHECK & NETWORK SYNC
    static void onReload(ModConfigEvent.Reloading event) {
        ModConfig config = event.getConfig();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !ModConfigs.worldFiles().containsKey(config.getFileName())) {
            return;
        }
        server.execute(() -> send(config.getFileName()));
    }

    public static boolean mayEdit(ServerPlayer player) {
        return player.server.isSingleplayerOwner(player.getGameProfile())
                || PowerRules.isOwner(player.getGameProfile());
    }

    public static void edit(ServerPlayer player, List<WorldSettingsEditPayload.Entry> entries) {
        if (!mayEdit(player)) {
            player.displayClientMessage(Component.translatable("config." + MagicMadness.MODID + ".denied"), false);
            return;
        }
        Set<String> changed = new LinkedHashSet<>();
        for (WorldSettingsEditPayload.Entry entry : entries) {
            ModConfigSpec spec = ModConfigs.worldFiles().get(entry.file());
            if (spec == null || !spec.isLoaded()
                    || !(spec.getSpec().get(entry.path()) instanceof ModConfigSpec.ValueSpec rule)) {
                continue;
            }
            Object value = spec.getValues().get(entry.path());
            if (value instanceof ModConfigSpec.IntValue whole) {
                int number = (int) Math.round(entry.value());
                if (rule.test(number)) {
                    whole.set(number);
                    changed.add(entry.file());
                }
            } else if (value instanceof ModConfigSpec.DoubleValue decimal && Double.isFinite(entry.value())
                    && rule.test(entry.value())) {
                decimal.set(entry.value());
                changed.add(entry.file());
            }
        }
        for (String file : changed) {
            saveSpec(file, ModConfigs.worldFiles().get(file));
            send(file);
        }
        if (!changed.isEmpty()) {
            MagicMadness.LOGGER.info("{} updated server settings in {}", player.getGameProfile().getName(), changed);
        }
    }

    public static void sendAllToPlayer(ServerPlayer player) {
        for (String file : ModConfigs.worldFiles().keySet()) {
            Path target = FMLPaths.CONFIGDIR.get().resolve(file).toAbsolutePath().normalize();
            if (Files.exists(target)) {
                try {
                    byte[] contents = Files.readAllBytes(target);
                    PacketDistributor.sendToPlayer(player, new WorldSettingsPayload(file, contents));
                } catch (IOException | IllegalStateException e) {
                    MagicMadness.LOGGER.warn("Could not sync {} to {}", file, player.getGameProfile().getName(), e);
                }
            }
        }
    }

    private static void send(String file) {
        Path target = FMLPaths.CONFIGDIR.get().resolve(file).toAbsolutePath().normalize();
        if (!Files.exists(target)) {
            return;
        }
        byte[] contents;
        try {
            contents = Files.readAllBytes(target);
        } catch (IOException | IllegalStateException e) {
            MagicMadness.LOGGER.warn("Could not send changed {} to players", file, e);
            return;
        }
        PacketDistributor.sendToAllPlayers(new WorldSettingsPayload(file, contents));
    }
    // #endregion
}
