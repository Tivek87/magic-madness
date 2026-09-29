package com.magicmadness.spell.client;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import com.magicmadness.spell.Spell;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
// #endregion

// ============================================================================
// MAGIC MADNESS — CLIENT SPELL COOLDOWN TRACKER & INTERPOLATION
// ============================================================================
@EventBusSubscriber(modid = MagicMadness.MODID, value = Dist.CLIENT)
public final class ClientSpellCooldowns {

    // #region 2. STATE & ACCESSORS
    private static final class Entry {
        int remainingTicks;
        int totalTicks;

        Entry(int remainingTicks, int totalTicks) {
            this.remainingTicks = remainingTicks;
            this.totalTicks = Math.max(1, totalTicks);
        }
    }

    private static final Map<Spell, Entry> COOLDOWNS = new EnumMap<>(Spell.class);

    private ClientSpellCooldowns() {}

    public static void set(String spellId, int remainingTicks, int totalTicks) {
        Spell spell = Spell.byId(spellId);
        if (spell == null) {
            return;
        }
        if (remainingTicks <= 0) {
            COOLDOWNS.remove(spell);
        } else {
            COOLDOWNS.put(spell, new Entry(remainingTicks, totalTicks));
        }
    }

    public static boolean isOnCooldown(Spell spell) {
        Entry e = COOLDOWNS.get(spell);
        return e != null && e.remainingTicks > 0;
    }

    public static float remainingSeconds(Spell spell, float partialTick) {
        Entry e = COOLDOWNS.get(spell);
        if (e == null || e.remainingTicks <= 0) {
            return 0.0F;
        }
        float exactTicks = Math.max(0.0F, e.remainingTicks - partialTick);
        return exactTicks / 20.0F;
    }

    public static float progress(Spell spell, float partialTick) {
        Entry e = COOLDOWNS.get(spell);
        if (e == null || e.remainingTicks <= 0) {
            return 0.0F;
        }
        float exactTicks = Math.max(0.0F, e.remainingTicks - partialTick);
        return Mth.clamp(exactTicks / (float) e.totalTicks, 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            COOLDOWNS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        COOLDOWNS.values().removeIf(entry -> {
            entry.remainingTicks--;
            return entry.remainingTicks <= 0;
        });
    }
    // #endregion
}
