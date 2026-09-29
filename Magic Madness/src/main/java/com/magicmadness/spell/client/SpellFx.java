package com.magicmadness.spell.client;

// #region 1. IMPORTS
import com.magicmadness.MagicMadness;
import com.magicmadness.engine.client.render.ConstructPainter;
import com.magicmadness.network.SpellFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
// #endregion

// ============================================================================
// MAGIC MADNESS — CLIENT 3D SPELL VFX MANAGER & RENDER DISPATCHER
// ============================================================================
@EventBusSubscriber(modid = MagicMadness.MODID, value = Dist.CLIENT)
public final class SpellFx {

    // #region 2. CONSTRUCT INTERFACE & REGISTRY
    public interface Construct {
        boolean tick();
        void render(PoseStack poseStack, VertexConsumer vc, float partialTick, float exactAge);
        default int casterId() {
            return -1;
        }
        default boolean isFlightConstruct() {
            return false;
        }
    }

    private static final int MAX_CONSTRUCTS = 64;
    private static final List<Construct> CONSTRUCTS = new ArrayList<>();

    private SpellFx() {}

    public static void add(Construct construct) {
        if (CONSTRUCTS.size() >= MAX_CONSTRUCTS) {
            CONSTRUCTS.removeFirst();
        }
        CONSTRUCTS.add(construct);
    }

    public static void stopFlightForCaster(int casterId) {
        CONSTRUCTS.removeIf(c -> c.isFlightConstruct() && c.casterId() == casterId);
    }

    public static void handle(SpellFxPayload payload) {
        switch (payload.kind()) {
            case FIREBALL_FLIGHT -> FireFx.spawnFlight(payload);
            case FIREBALL_BLAST -> FireFx.spawnBlast(payload);
            case FIREBALL_DOUSE -> stopFlightForCaster(payload.casterId());
            case FIREBALL_ASH -> FireFx.spawnAsh(payload);
            case LIGHTNING_CHARGE -> LightningFx.spawnCharge(payload);
            case LIGHTNING_BOLT -> LightningFx.spawnBolt(payload);
            case LIGHTNING_ARC -> LightningFx.spawnArc(payload);
            case WIND_GUST -> WindFx.spawnGust(payload);
        }
    }
    // #endregion

    // #region 3. CLIENT TICK & 60-240+ FPS WORLD RENDERING
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CONSTRUCTS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Iterator<Construct> it = CONSTRUCTS.iterator();
        while (it.hasNext()) {
            if (!it.next().tick()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CONSTRUCTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vc = ConstructPainter.buffer(bufferSource);

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        for (int i = 0; i < CONSTRUCTS.size(); i++) {
            Construct c = CONSTRUCTS.get(i);
            c.render(poseStack, vc, partialTick, partialTick);
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }
    // #endregion
}
