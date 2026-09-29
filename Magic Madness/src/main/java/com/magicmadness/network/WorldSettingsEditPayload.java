package com.magicmadness.network;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
// #endregion

// ============================================================================
// MAGIC MADNESS — CLIENT-TO-SERVER WORLD SETTINGS EDIT PAYLOAD (OWNER/HOST ONLY)
// ============================================================================
public record WorldSettingsEditPayload(List<Entry> entries) implements CustomPacketPayload {

    // #region 2. PAYLOAD TYPE, ENTRY RECORD & CODEC
    public static final CustomPacketPayload.Type<WorldSettingsEditPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MagicMadness.MODID, "world_settings_edit"));

    public record Entry(String file, List<String> path, double value) {
        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Entry::file,
                ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list(8)), Entry::path,
                ByteBufCodecs.DOUBLE, Entry::value,
                Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, WorldSettingsEditPayload> STREAM_CODEC =
            StreamCodec.composite(Entry.STREAM_CODEC.apply(ByteBufCodecs.list(4096)),
                    WorldSettingsEditPayload::entries, WorldSettingsEditPayload::new);

    @Override
    public CustomPacketPayload.Type<WorldSettingsEditPayload> type() {
        return TYPE;
    }
    // #endregion
}
