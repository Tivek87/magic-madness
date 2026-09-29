package com.magicmadness.mixin;

// #region 1. IMPORTS
import com.magicmadness.config.WorldSettings;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
// #endregion

// ============================================================================
// MAGIC MADNESS — SERVER CONFIG PREPARATION MIXIN
// ============================================================================
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    // #region 2. PREPARE WORLD CONFIGS BEFORE SERVER RUN
    @Inject(method = "runServer", at = @At("HEAD"))
    private void magicmadness$worldSettings(CallbackInfo info) {
        WorldSettings.prepare((MinecraftServer) (Object) this);
    }
    // #endregion
}
