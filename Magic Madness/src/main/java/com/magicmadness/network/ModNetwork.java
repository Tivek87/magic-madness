package com.magicmadness.network;

// #region 1. IMPORTS
import com.magicmadness.config.WorldSettings;
import com.magicmadness.network.client.ClientPayloadHandler;
import com.magicmadness.spell.SpellCasting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
// #endregion

// ============================================================================
// MAGIC MADNESS — NETWORK PAYLOAD REGISTRAR
// ============================================================================
public final class ModNetwork {

    // #region 2. CONSTANTS & REGISTRATION
    private static final String VERSION = "1";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(WorldSettingsPayload.TYPE, WorldSettingsPayload.STREAM_CODEC,
                ModNetwork::onWorldSettings);
        registrar.playToServer(WorldSettingsEditPayload.TYPE, WorldSettingsEditPayload.STREAM_CODEC,
                ModNetwork::onWorldSettingsEdit);
        registrar.playToServer(CastSpellPayload.TYPE, CastSpellPayload.STREAM_CODEC,
                ModNetwork::onCastSpell);
        registrar.playToClient(SpellCooldownPayload.TYPE, SpellCooldownPayload.STREAM_CODEC,
                ModNetwork::onSpellCooldown);
        registrar.playToClient(SpellFxPayload.TYPE, SpellFxPayload.STREAM_CODEC,
                ModNetwork::onSpellFx);
    }
    // #endregion

    // #region 3. HANDLERS
    private static void onWorldSettings(WorldSettingsPayload payload, IPayloadContext context) {
        ClientPayloadHandler.handleWorldSettings(payload, context);
    }

    private static void onWorldSettingsEdit(WorldSettingsEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                WorldSettings.edit(serverPlayer, payload.entries());
            }
        });
    }

    private static void onCastSpell(CastSpellPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                SpellCasting.tryCast(serverPlayer, payload.spellId());
            }
        });
    }

    private static void onSpellCooldown(SpellCooldownPayload payload, IPayloadContext context) {
        ClientPayloadHandler.handleSpellCooldown(payload, context);
    }

    private static void onSpellFx(SpellFxPayload payload, IPayloadContext context) {
        ClientPayloadHandler.handleSpellFx(payload, context);
    }
    // #endregion
}
