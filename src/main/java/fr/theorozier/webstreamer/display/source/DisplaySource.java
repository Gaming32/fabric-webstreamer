package fr.theorozier.webstreamer.display.source;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

public interface DisplaySource {

    String getType();

    URI getUri();

    void resetUri();

    String getStatus();

    void writeNbt(CompoundTag nbt);

    void readNbt(CompoundTag nbt);

    @NotNull
    static DisplaySource newSourceFromType(String type) {
        return switch (type) {
            case RawDisplaySource.TYPE -> new RawDisplaySource();
            case TwitchDisplaySource.TYPE -> new TwitchDisplaySource();
            default -> NullDisplaySource.INSTANCE;
        };
    }

}
