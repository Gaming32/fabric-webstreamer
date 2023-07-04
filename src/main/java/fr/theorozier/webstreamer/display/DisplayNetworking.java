package fr.theorozier.webstreamer.display;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Block;

import java.util.function.BiConsumer;

public class DisplayNetworking {

    public static final ResourceLocation DISPLAY_BLOCK_UPDATE_PACKET_ID = new ResourceLocation("webstreamer:display_block_update");

    private static FriendlyByteBuf encodeDisplayUpdatePacket(DisplayBlockEntity blockEntity) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(blockEntity.getBlockPos());
        CompoundTag comp = new CompoundTag();
        blockEntity.saveAdditional(comp);
        buf.writeNbt(comp);
        return buf;
    }

    private static void decodeDisplayUpdatePacket(FriendlyByteBuf buf, BiConsumer<BlockPos, CompoundTag> consumer) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag nbt = buf.readNbt();
        consumer.accept(pos, nbt);
    }

    /**
     * Client-side only, send a display update packet to the server.
     * @param blockEntity The display block entity.
     */
    @Environment(EnvType.CLIENT)
    public static void sendDisplayUpdate(DisplayBlockEntity blockEntity) {
        ClientPlayNetworking.send(DISPLAY_BLOCK_UPDATE_PACKET_ID, encodeDisplayUpdatePacket(blockEntity));
    }

    /**
     * Server-side (integrated or dedicated) display packet receiver.
     */
    public static void registerDisplayUpdateReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(DISPLAY_BLOCK_UPDATE_PACKET_ID, new DisplayUpdateHandler());
    }

    private static class DisplayUpdateHandler implements ServerPlayNetworking.PlayChannelHandler {

        @Override
        public void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender) {
            if (DisplayBlock.canUse(player)) {
                decodeDisplayUpdatePacket(buf, (pos, nbt) -> {
                    ServerLevel world = player.getLevel();
                    world.getServer().executeIfPossible(() -> {
                        if (world.getBlockEntity(pos) instanceof DisplayBlockEntity blockEntity) {
                            blockEntity.load(nbt);
                            blockEntity.setChanged();
                            world.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), Block.UPDATE_ALL);
                        }
                    });
                });
            }
        }
    }

}
