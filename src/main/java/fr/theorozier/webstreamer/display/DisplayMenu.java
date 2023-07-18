package fr.theorozier.webstreamer.display;

import fr.theorozier.webstreamer.WebStreamer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class DisplayMenu extends AbstractContainerMenu {
    private final BlockPos pos;

    public DisplayMenu(int syncId, Inventory inventory, FriendlyByteBuf buf) {
        super(WebStreamer.DISPLAY_SCREEN_HANDLER, syncId);
        pos = buf.readBlockPos();
    }

    public DisplayMenu(int syncId, BlockPos pos) {
        super(WebStreamer.DISPLAY_SCREEN_HANDLER, syncId);
        this.pos = pos;
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return DisplayBlock.canUse(player);
    }

    public BlockPos getPos() {
        return pos;
    }
}
