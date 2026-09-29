package com.magicmadness.engine.tick;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
// #endregion

// ============================================================================
// MAGIC MADNESS — SERVER TICK-DRIVEN SPELL TASK SCHEDULER
// ============================================================================
@EventBusSubscriber(modid = MagicMadness.MODID)
public final class Effects {

    // #region 2. TASK INTERFACE & ACTIVE STORAGE
    @FunctionalInterface
    public interface Task {
        boolean tick(ServerLevel level, int age);
    }

    private static final class Running {
        final ResourceKey<Level> dimension;
        final Task task;
        int age;

        Running(ResourceKey<Level> dimension, Task task) {
            this.dimension = dimension;
            this.task = task;
        }
    }

    private static final List<Running> ACTIVE = new ArrayList<>();
    private static final List<Running> PENDING = new ArrayList<>();

    private Effects() {}
    // #endregion

    // #region 3. SCHEDULING & SERVER TICK EXECUTION
    public static void start(ServerLevel level, Task task) {
        if (task.tick(level, 0)) {
            Running r = new Running(level.dimension(), task);
            r.age = 1;
            PENDING.add(r);
        }
    }

    public static void loop(int totalTicks, IntConsumer tickAction) {
        if (totalTicks <= 0) {
            return;
        }
        tickAction.accept(0);
        if (totalTicks > 1) {
            Running r = new Running(Level.OVERWORLD, (lvl, age) -> {
                tickAction.accept(age);
                return age + 1 < totalTicks;
            });
            r.age = 1;
            PENDING.add(r);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!PENDING.isEmpty()) {
            ACTIVE.addAll(PENDING);
            PENDING.clear();
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Running> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            ServerLevel level = server.getLevel(r.dimension);
            if (level == null) {
                level = server.overworld();
            }
            if (level == null || !r.task.tick(level, r.age++)) {
                it.remove();
            }
        }
    }
    // #endregion
}
