package fr.theorozier.webstreamer.display;

import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import fr.theorozier.webstreamer.display.source.NullDisplaySource;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class DisplayBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    private DisplaySource source = NullDisplaySource.INSTANCE;
    private float width = 1;
    private float height = 1;
    private float audioDistance = 10f;
    private float audioVolume = 1f;
    private UUID uuid = Mth.createInsecureUUID();

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(WebStreamerMod.DISPLAY_BLOCK_ENTITY, pos, state);
    }

    public void setSource(@NotNull DisplaySource source) {
        Objects.requireNonNull(source);
        this.source = source;
        this.markRenderDataSourceDirty();
        this.setChanged();
    }

    @NotNull
    public DisplaySource getSource() {
        return source;
    }

    /**
     * Reset the internal source URI, that is currently used with Twitch sources in order
     * to reset the channels' cache. This also mark the render data as dirty in order to
     * update the URI on next render.
     */
    public void resetSourceUri() {
        this.source.resetUri();
        this.markRenderDataSourceDirty();
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        this.setChanged();
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float calcWidthOffset() {
        return (this.width - 1) / -2f;
    }

    public float calcHeightOffset() {
        return (this.height - 1) / -2f;
    }

    public void setAudioConfig(float distance, float volume) {
        this.audioDistance = distance;
        this.audioVolume = volume;
        this.setChanged();
    }

    public float getAudioDistance() {
        return audioDistance;
    }

    public float getAudioVolume() {
        return audioVolume;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbt) {

        super.saveAdditional(nbt);

        CompoundTag displayNbt = new CompoundTag();
        nbt.put("display", displayNbt);

        displayNbt.putFloat("width", this.width);
        displayNbt.putFloat("height", this.height);
        displayNbt.putFloat("audioDistance", this.audioDistance);
        displayNbt.putFloat("audioVolume", this.audioVolume);

        if (this.source != null) {
            displayNbt.putString("type", this.source.getType());
            this.source.writeNbt(displayNbt);
        } else {
            displayNbt.putString("type", "");
        }

        displayNbt.putUUID("uuid", uuid);

    }

    @Override
    public void load(@NotNull CompoundTag nbt) {

        super.load(nbt);

        if (nbt.get("display") instanceof CompoundTag displayNbt) {

            if (displayNbt.get("width") instanceof FloatTag width) {
                this.width = width.getAsFloat();
            } else {
                this.width = 1;
            }

            if (displayNbt.get("height") instanceof FloatTag height) {
                this.height = height.getAsFloat();
            } else {
                this.height = 1;
            }

            if (displayNbt.get("audioDistance") instanceof FloatTag audioDistance) {
                this.audioDistance = audioDistance.getAsFloat();
            } else {
                this.audioDistance = 10f;
            }

            if (displayNbt.get("audioVolume") instanceof FloatTag audioVolume) {
                this.audioVolume = audioVolume.getAsFloat();
            } else {
                this.audioVolume = 1f;
            }

            if (displayNbt.get("type") instanceof StringTag type) {
                this.source = DisplaySource.newSourceFromType(type.getAsString());
                this.source.readNbt(displayNbt);
            } else {
                this.source = NullDisplaySource.INSTANCE;
            }

            if (displayNbt.hasUUID("uuid")) {
                this.uuid = displayNbt.getUUID("uuid");
            }

            this.markRenderDataSourceDirty();

        }

    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    /** Utility method to make a log message prefixed by this display's position. */
    public String makeLog(String message) {
        return "[" + this.worldPosition.getX() + "/" + this.worldPosition.getY() + "/" + this.worldPosition.getZ() + "] " + message;
    }

    // Render data //

    private final Object cachedRenderDataGuard = new Object();
    private Object cachedRenderData;

    /**
     * <b>Should only be called from client side.</b>
     * @return A <code>DisplayRenderData</code> class, only valid on client side.
     */
    public Object getRenderData() {
        synchronized (this.cachedRenderDataGuard) {
            if (this.cachedRenderData == null) {
                this.cachedRenderData = new fr.theorozier.webstreamer.display.render.DisplayRenderData(this);
            }
            return this.cachedRenderData;
        }
    }

    /**
     * This internal method is used to mark internal render data URL as dirty.
     * This might be used from any side and is thread-safe.
     * This will actually do something only on the client-side and only if the
     * render data has been requested before.
     */
    private void markRenderDataSourceDirty() {
        synchronized (this.cachedRenderDataGuard) {
            if (this.cachedRenderData != null) {
                ((fr.theorozier.webstreamer.display.render.DisplayRenderData) this.cachedRenderData).markSourceDirty();
            }
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public void newUuid() {
        uuid = Mth.createInsecureUUID();
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new DisplayMenu(syncId, worldPosition);
    }
}
