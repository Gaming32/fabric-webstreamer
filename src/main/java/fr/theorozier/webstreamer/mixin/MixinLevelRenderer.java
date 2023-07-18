package fr.theorozier.webstreamer.mixin;

import fr.theorozier.webstreamer.WebStreamerClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void render(CallbackInfo info) {
        WebStreamerClient.DISPLAY_LAYERS.tick();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    public void setWorld(@Nullable ClientLevel world, CallbackInfo info) {
        if (world == null) {
            WebStreamerClient.DISPLAY_LAYERS.clear();
        }
    }

}
