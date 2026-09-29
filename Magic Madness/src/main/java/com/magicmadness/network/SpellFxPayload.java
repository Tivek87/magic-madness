package com.magicmadness.network;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
// #endregion

// ============================================================================
// MAGIC MADNESS — SPELL 3D VFX PAYLOAD (SERVER -> CLIENT)
// ============================================================================
public record SpellFxPayload(
        Kind kind,
        int casterId,
        Vec3 start,
        Vec3 end,
        float radius,
        int durationTicks
) implements CustomPacketPayload {

    // #region 2. VFX KINDS & CODEC
    public enum Kind {
        FIREBALL_FLIGHT,
        FIREBALL_BLAST,
        FIREBALL_DOUSE,
        FIREBALL_ASH,
        LIGHTNING_CHARGE,
        LIGHTNING_BOLT,
        LIGHTNING_ARC,
        WIND_GUST
    }

    public static final CustomPacketPayload.Type<SpellFxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MagicMadness.MODID, "spell_fx"));

    public static final StreamCodec<FriendlyByteBuf, SpellFxPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpellFxPayload::write, SpellFxPayload::new);

    private SpellFxPayload(FriendlyByteBuf buf) {
        this(
                buf.readEnum(Kind.class),
                buf.readVarInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(),
                buf.readVarInt()
        );
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(this.kind);
        buf.writeVarInt(this.casterId);
        buf.writeDouble(this.start.x);
        buf.writeDouble(this.start.y);
        buf.writeDouble(this.start.z);
        buf.writeDouble(this.end.x);
        buf.writeDouble(this.end.y);
        buf.writeDouble(this.end.z);
        buf.writeFloat(this.radius);
        buf.writeVarInt(this.durationTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    // #endregion
}
