package com.magicmadness.network.client;

// #region 1. IMPORTS
import com.magicmadness.config.ModConfigs;
import com.magicmadness.config.WorldSettings;
import com.magicmadness.network.SpellCooldownPayload;
import com.magicmadness.network.SpellFxPayload;
import com.magicmadness.network.WorldSettingsPayload;
import com.magicmadness.spell.client.ClientSpellCooldowns;
import com.magicmadness.spell.client.SpellFx;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
// #endregion

// ============================================================================
// MAGIC MADNESS — CLIENT PAYLOAD HANDLER
// ============================================================================
public final class ClientPayloadHandler {

    // #region 2. WORLD SETTINGS & SPELL PAYLOAD HANDLERS
    private ClientPayloadHandler() {}

    public static void handleWorldSettings(WorldSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().isLocalServer() || !ModConfigs.worldFiles().containsKey(payload.file())) {
                return;
            }
            WorldSettings.applySyncedBytes(payload.file(), payload.contents());
        });
    }

    public static void handleSpellCooldown(SpellCooldownPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSpellCooldowns.set(
                payload.spellId(), payload.remainingTicks(), payload.totalTicks()));
    }

    public static void handleSpellFx(SpellFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SpellFx.handle(payload));
    }
    // #endregion
}
