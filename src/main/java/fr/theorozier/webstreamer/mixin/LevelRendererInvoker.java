package fr.theorozier.webstreamer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {

    @Invoker
    static void invokeRenderShape(PoseStack matrices, VertexConsumer vertexConsumer, VoxelShape voxelShape, double dx, double dy, double dz, float r, float g, float b, float a) {
        throw new AssertionError();
    }

}
