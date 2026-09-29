package com.magicmadness.spell;

// #region 1. IMPORTS
import com.magicmadness.config.PowerRules;
import com.magicmadness.network.SpellCooldownPayload;
import com.magicmadness.spell.air.WindGustSpell;
import com.magicmadness.spell.electric.LightningSpell;
import com.magicmadness.spell.fire.FireballSpell;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
// #endregion

// ============================================================================
// MAGIC MADNESS — SERVER SPELL DISPATCH & COOLDOWN TRACKER
// ============================================================================
public final class SpellCasting {

    // #region 2. COOLDOWN STORAGE & DISPATCH
    private record CooldownStamp(long readyGameTime, int totalTicks) {}

    private static final Map<UUID, EnumMap<Spell, CooldownStamp>> COOLDOWNS = new ConcurrentHashMap<>();

    private SpellCasting() {}

    public static void tryCast(ServerPlayer player, String spellId) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        Spell spell = Spell.byId(spellId);
        if (spell == null || !PowerRules.isSpellAllowed(spell.id())) {
            return;
        }

        long now = player.serverLevel().getGameTime();
        int remaining = remainingTicks(player, spell, now);
        if (remaining > 0) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35F, 1.6F);
            CooldownStamp stamp = getStamp(player.getUUID(), spell);
            int total = stamp != null ? stamp.totalTicks() : Math.max(1, SpellTargets.scaleCooldown(spell.cooldownTicks()));
            PacketDistributor.sendToPlayer(player, new SpellCooldownPayload(spell.id(), remaining, total));
            return;
        }

        boolean casted = switch (spell) {
            case FIREBALL -> FireballSpell.cast(player);
            case LIGHTNING -> LightningSpell.cast(player);
            case WIND_GUST -> WindGustSpell.cast(player);
        };

        if (casted) {
            int totalCooldown = SpellTargets.scaleCooldown(spell.cooldownTicks());
            if (totalCooldown > 0) {
                COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Spell.class))
                        .put(spell, new CooldownStamp(now + totalCooldown, totalCooldown));
                PacketDistributor.sendToPlayer(player, new SpellCooldownPayload(spell.id(), totalCooldown, totalCooldown));
            }
        }
    }

    public static int remainingTicks(ServerPlayer player, Spell spell, long nowGameTime) {
        CooldownStamp stamp = getStamp(player.getUUID(), spell);
        if (stamp == null) {
            return 0;
        }
        long diff = stamp.readyGameTime() - nowGameTime;
        return diff > 0L ? (int) diff : 0;
    }

    private static CooldownStamp getStamp(UUID uuid, Spell spell) {
        EnumMap<Spell, CooldownStamp> map = COOLDOWNS.get(uuid);
        return map != null ? map.get(spell) : null;
    }
    // #endregion
}
