package com.magicmadness.events;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import com.magicmadness.config.WorldSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
// #endregion

// ============================================================================
// MAGIC MADNESS — WORLD START / PLAYER JOIN CHAT & CONFIG SYNC HANDLER
// ============================================================================
@EventBusSubscriber(modid = MagicMadness.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WorldStartEvents {

    // #region 2. PRE-ALLOCATED CHAT COMPONENT (ZERO TICK/JOIN ALLOCATION)
    private static final Component WORKING_MSG = Component.literal("Mod is working.");

    private WorldStartEvents() {}
    // #endregion

    // #region 3. WORLD START CHAT NOTIFICATION & WORLD SETTINGS SYNC
    @SubscribeEvent
    public static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        final Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            player.sendSystemMessage(WORKING_MSG);
            if (player instanceof ServerPlayer serverPlayer) {
                WorldSettings.sendAllToPlayer(serverPlayer);
            }
        }
    }
    // #endregion
}
