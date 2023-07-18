package fr.theorozier.webstreamer.display;

import fr.theorozier.webstreamer.WebStreamer;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DisplayBlock extends BaseEntityBlock {

    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE_NORTH = Shapes.box(0, 0, 0.9, 1, 1, 1);
    private static final VoxelShape SHAPE_SOUTH = Shapes.box(0, 0, 0, 1, 1, 0.1);
    private static final VoxelShape SHAPE_WEST = Shapes.box(0.9, 0, 0, 1, 1, 1);
    private static final VoxelShape SHAPE_EAST = Shapes.box(0, 0, 0, 0.1, 1, 1);

    public DisplayBlock() {
        super(Properties.of(Material.GLASS)
                .sound(SoundType.GLASS)
                .strength(-1.0f, 3600000.0f)
                .requiresCorrectToolForDrops()
                .noLootTable()
                .noOcclusion());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DisplayBlockEntity(pos, state);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction dir = ctx.getClickedFace();
        if (dir == Direction.DOWN || dir == Direction.UP) {
            dir = ctx.getHorizontalDirection().getOpposite();
        }
        return this.defaultBlockState().setValue(HORIZONTAL_FACING, dir);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return switch (state.getValue(HORIZONTAL_FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> throw new AssertionError();
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        level.scheduleTick(pos, this, 1);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof DisplayBlockEntity entity)) {
            WebStreamer.LOGGER.warn("Couldn't find DisplayBlockEntity at {}", pos);
            level.scheduleTick(pos, this, 1);
            return;
        }
        entity.newUuid();
        final DisplaysSavedData data = DisplaysSavedData.get(level);
        if (data.getDisplays().put(entity.getUuid(), new DisplaysSavedData.SavedDisplay(
            entity.getUuid(), level.dimension(), pos
        )) != null) {
            WebStreamer.LOGGER.warn("Replaced duplicate display {}", entity.getUuid());
        }
        data.setDirty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel serverLevel) {
            if (!(level.getBlockEntity(pos) instanceof DisplayBlockEntity entity)) {
                WebStreamer.LOGGER.warn("Couldn't find DisplayBlockEntity at {}", pos);
                level.scheduleTick(pos, this, 1);
            } else {
                final DisplaysSavedData data = DisplaysSavedData.get(serverLevel);
                if (data.getDisplays().remove(entity.getUuid()) == null) {
                    WebStreamer.LOGGER.warn("Failed to remove non-existent display {}", entity.getUuid());
                }
                data.setDirty();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DisplayBlockEntity dbe) {
            if (level.isClientSide) {
                return InteractionResult.sidedSuccess(true);
            } else if (canUse(player)) {
                player.openMenu(dbe);
                return InteractionResult.sidedSuccess(false);
            }
        }
        return InteractionResult.PASS;
    }

    public static boolean canUse(@NotNull Player player) {
        return Permissions.check(player, "webstreamer.display.use", 2);
    }

}
