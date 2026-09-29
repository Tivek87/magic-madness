package com.magicmadness.network;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
// #endregion

// ============================================================================
// MAGIC MADNESS — CAST SPELL PAYLOAD (CLIENT -> SERVER)
// ============================================================================
public record CastSpellPayload(String spellId) implements CustomPacketPayload {

    // #region 2. TYPE & STREAM CODEC
    public static final CustomPacketPayload.Type<CastSpellPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MagicMadness.MODID, "cast_spell"));

    public static final StreamCodec<ByteBuf, CastSpellPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CastSpellPayload::spellId,
            CastSpellPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    // #endregion
}
