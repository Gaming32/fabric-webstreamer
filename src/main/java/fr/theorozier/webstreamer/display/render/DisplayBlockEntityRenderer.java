package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import fr.theorozier.webstreamer.WebStreamerClient;
import fr.theorozier.webstreamer.WebStreamer;
import fr.theorozier.webstreamer.display.DisplayBlock;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.url.DisplayUrl;
import fr.theorozier.webstreamer.mixin.LevelRendererInvoker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.stream.StreamSupport;

@Environment(EnvType.CLIENT)
public class DisplayBlockEntityRenderer implements BlockEntityRenderer<DisplayBlockEntity> {

    private static final Component NO_LAYER_AVAILABLE_TEXT = Component.translatable("gui.webstreamer.display.status.noLayerAvailable");
    private static final Component UNKNOWN_FORMAT_TEXT = Component.translatable("gui.webstreamer.display.status.unknownFormat");
    private static final Component NO_URL_TEXT = Component.translatable("gui.webstreamer.display.status.noUrl");

    private final GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;
    private final Font textRenderer;

    @SuppressWarnings("unused")
    public DisplayBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.textRenderer = ctx.getFont();
    }

    @Override
    public void render(DisplayBlockEntity entity, float tickDelta, @NotNull PoseStack matrices, @NotNull MultiBufferSource vertexConsumers, int light, int overlay) {
        assert entity.getLevel() != null;

        DisplayRenderData renderData = (DisplayRenderData) entity.getRenderData();
        DisplayLayerManager layerManager = WebStreamerClient.DISPLAY_LAYERS;

        DisplayUrl url = renderData.getUrl(layerManager.getResources().getExecutor());

        Player player = Minecraft.getInstance().player;

        Component statusText = null;

        if (player != null) {

            boolean hasDisplayEquipped = StreamSupport.stream(player.getAllSlots().spliterator(), false)
                    .map(ItemStack::getItem)
                    .anyMatch(WebStreamer.DISPLAY_ITEM::equals);

            if (hasDisplayEquipped) {

                VoxelShape displayShape = entity.getBlockState().getShape(entity.getLevel(), entity.getBlockPos());
                LevelRendererInvoker.invokeRenderShape(matrices, vertexConsumers.getBuffer(RenderType.lines()), displayShape, 0, 0, 0, 235 / 255f, 168 / 255f, 0f, 1f);

                statusText = Component.literal(entity.getSource().getStatus());

            }

        }

        if (url != null) {
            try {

                DisplayLayer layer = layerManager.getLayerForUrl(url);

                if (layer.isLost()) {
                    // Each time a display get here and the layer is lost, and then we request
                    // a URL reset for the render data. With twitch sources it should reset
                    // and re-fetch a new URL for the playlist.
                    entity.resetSourceUri();
                    return;
                }

                matrices.pushPose();
                Matrix4f positionMatrix = matrices.last().pose();

                VertexConsumer buffer = vertexConsumers.getBuffer(layer.getRenderLayer());

                BlockPos pos = entity.getBlockPos();
                float audioDistance = entity.getAudioDistance();
                float audioVolume = entity.getAudioVolume();
                layer.pushAudioSource(pos, pos.distManhattan(this.gameRenderer.getMainCamera().getBlockPosition()), audioDistance, audioVolume);

                // Width/Height start coords
                float ws = renderData.getWidthOffset();
                float hs = renderData.getHeightOffset();
                // Width/Height end coords
                float we = ws + entity.getWidth();
                float he = hs + entity.getHeight();

                switch (entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)) {
                    case NORTH -> {
                        buffer.vertex(positionMatrix, we, hs, 0.95f).uv(0, 1).endVertex();
                        buffer.vertex(positionMatrix, ws, hs, 0.95f).uv(1, 1).endVertex();
                        buffer.vertex(positionMatrix, ws, he, 0.95f).uv(1, 0).endVertex();
                        buffer.vertex(positionMatrix, we, he, 0.95f).uv(0, 0).endVertex();
                    }
                    case SOUTH -> {
                        buffer.vertex(positionMatrix, ws, hs, 0.05f).uv(0, 1).endVertex();
                        buffer.vertex(positionMatrix, we, hs, 0.05f).uv(1, 1).endVertex();
                        buffer.vertex(positionMatrix, we, he, 0.05f).uv(1, 0).endVertex();
                        buffer.vertex(positionMatrix, ws, he, 0.05f).uv(0, 0).endVertex();
                    }
                    case EAST -> {
                        buffer.vertex(positionMatrix, 0.05f, hs, we).uv(0, 1).endVertex();
                        buffer.vertex(positionMatrix, 0.05f, hs, ws).uv(1, 1).endVertex();
                        buffer.vertex(positionMatrix, 0.05f, he, ws).uv(1, 0).endVertex();
                        buffer.vertex(positionMatrix, 0.05f, he, we).uv(0, 0).endVertex();
                    }
                    case WEST -> {
                        buffer.vertex(positionMatrix, 0.95f, hs, ws).uv(0, 1).endVertex();
                        buffer.vertex(positionMatrix, 0.95f, hs, we).uv(1, 1).endVertex();
                        buffer.vertex(positionMatrix, 0.95f, he, we).uv(1, 0).endVertex();
                        buffer.vertex(positionMatrix, 0.95f, he, ws).uv(0, 0).endVertex();
                    }
                }

                matrices.popPose();

            } catch (DisplayLayerManager.OutOfLayerException e) {
                statusText = NO_LAYER_AVAILABLE_TEXT;
            } catch (DisplayLayerManager.UnknownFormatException e) {
                statusText = UNKNOWN_FORMAT_TEXT;
            }
        } else {
            statusText = NO_URL_TEXT;
        }

        if (statusText != null) {

            matrices.pushPose();

            final float scaleFactor = 128f / Math.min(entity.getWidth(), entity.getHeight());
            final float scale = 1f / scaleFactor;
            final float halfWidth = this.textRenderer.width(statusText) / scaleFactor / 2f;
            final float halfHeight = this.textRenderer.lineHeight / scaleFactor / 2f;

            switch (entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)) {
                case NORTH -> matrices.translate(0.5f + halfWidth, 0.5f + halfHeight, 0.85f);
                case SOUTH -> {
                    matrices.mulPose(Vector3f.YP.rotationDegrees(180));
                    matrices.translate(-0.5f + halfWidth, 0.5f + halfHeight, -0.15f);
                }
                case EAST -> {
                    matrices.mulPose(Vector3f.YP.rotationDegrees(270));
                    matrices.translate(0.5f + halfWidth, 0.5f + halfHeight, -0.15f);
                }
                case WEST -> {
                    matrices.mulPose(Vector3f.YP.rotationDegrees(90));
                    matrices.translate(-0.5f + halfWidth, 0.5f + halfHeight, 0.85f);
                }
            }

            matrices.scale(-scale, -scale, 1f);
            this.textRenderer.drawInBatch(statusText, 0f, 0f, 0x00ffffff, false, matrices.last().pose(), vertexConsumers, false, 0xBB222222, light);
            matrices.popPose();

        }

    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull DisplayBlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(DisplayBlockEntity blockEntity, Vec3 cameraPos) {
        final Direction.Axis facing = blockEntity.getBlockState().getValue(DisplayBlock.HORIZONTAL_FACING).getAxis();
        final double distance = getViewDistance();
        return AABB.ofSize(
            Vec3.atCenterOf(blockEntity.getBlockPos()),
            facing.choose(0, 1, 1) * blockEntity.getWidth() + 2 * distance,
            facing.choose(1, 0, 1) * blockEntity.getHeight() + 2 * distance,
            facing.choose(1, 1, 0) * blockEntity.getWidth() + 2 * distance
        ).contains(cameraPos);
    }
}
