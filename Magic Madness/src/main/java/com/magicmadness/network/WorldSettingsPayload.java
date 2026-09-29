package com.magicmadness.network;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
// #endregion

// ============================================================================
// MAGIC MADNESS — SERVER-TO-CLIENT WORLD SETTINGS SYNC PAYLOAD
// ============================================================================
public record WorldSettingsPayload(String file, byte[] contents) implements CustomPacketPayload {

    // #region 2. PAYLOAD TYPE & CODEC
    public static final CustomPacketPayload.Type<WorldSettingsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MagicMadness.MODID, "world_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorldSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WorldSettingsPayload::file,
            ByteBufCodecs.BYTE_ARRAY, WorldSettingsPayload::contents,
            WorldSettingsPayload::new);

    @Override
    public CustomPacketPayload.Type<WorldSettingsPayload> type() {
        return TYPE;
    }
    // #endregion
}
