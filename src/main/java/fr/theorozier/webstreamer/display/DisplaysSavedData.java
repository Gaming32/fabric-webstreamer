package fr.theorozier.webstreamer.display;

import fr.theorozier.webstreamer.WebStreamerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DisplaysSavedData extends SavedData {
    public static class SavedDisplay {
        private UUID uuid;
        private ResourceKey<Level> dimension;
        private BlockPos location;

        public SavedDisplay(UUID uuid, ResourceKey<Level> dimension, BlockPos location) {
            this.uuid = uuid;
            this.dimension = dimension;
            this.location = location;
        }

        public SavedDisplay(CompoundTag tag) {
            uuid = tag.getUUID("UUID");
            final ResourceLocation dimensionLocation = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimensionLocation != null) {
                dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionLocation);
            }
            location = NbtUtils.readBlockPos(tag.getCompound("Location"));
        }

        public CompoundTag save() {
            final CompoundTag nbt = new CompoundTag();
            nbt.putUUID("UUID", uuid);
            nbt.putString("Dimension", dimension.location().toString());
            nbt.put("Location", NbtUtils.writeBlockPos(location));
            return nbt;
        }

        public boolean checkValid() {
            if (dimension == null) {
                WebStreamerMod.LOGGER.warn("Invalid SavedDisplay " + uuid + ": dimension == null");
                return false;
            }
            return true;
        }
    }

    private final Map<UUID, SavedDisplay> displays = new HashMap<>();

    @NotNull
    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        final ListTag list = new ListTag();
        compoundTag.put("Displays", list);
        displays.values()
            .stream()
            .map(SavedDisplay::save)
            .forEach(list::add);
        return compoundTag;
    }

    public Map<UUID, SavedDisplay> getDisplays() {
        return displays;
    }

    public static DisplaysSavedData get(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            level = level.getServer().overworld();
        }
        return level.getDataStorage().computeIfAbsent(nbt -> {
            final DisplaysSavedData data = new DisplaysSavedData();
            nbt.getList("Displays", Tag.TAG_COMPOUND)
                .stream()
                .map(t -> (CompoundTag)t)
                .map(SavedDisplay::new)
                .filter(SavedDisplay::checkValid)
                .forEach(d -> data.displays.put(d.uuid, d));
            return data;
        }, DisplaysSavedData::new, "stream_displays");
    }
}
