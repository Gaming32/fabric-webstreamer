package fr.theorozier.webstreamer.display;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class DisplayBlockItem extends BlockItem {

    public DisplayBlockItem(Block block, Properties settings) {
        super(block, settings);
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        Player playerEntity = context.getPlayer();
        return playerEntity != null && !DisplayBlock.canUse(playerEntity) ? null : super.getPlacementState(context);
    }

}
