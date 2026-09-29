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
// MAGIC MADNESS — SPELL COOLDOWN SYNC PAYLOAD (SERVER -> CLIENT)
// ============================================================================
public record SpellCooldownPayload(String spellId, int remainingTicks, int totalTicks) implements CustomPacketPayload {

    // #region 2. TYPE & STREAM CODEC
    public static final CustomPacketPayload.Type<SpellCooldownPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MagicMadness.MODID, "spell_cooldown"));

    public static final StreamCodec<ByteBuf, SpellCooldownPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SpellCooldownPayload::spellId,
            ByteBufCodecs.VAR_INT, SpellCooldownPayload::remainingTicks,
            ByteBufCodecs.VAR_INT, SpellCooldownPayload::totalTicks,
            SpellCooldownPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    // #endregion
}
