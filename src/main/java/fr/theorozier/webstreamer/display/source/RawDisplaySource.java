package fr.theorozier.webstreamer.display.source;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;

import java.net.URI;

public class RawDisplaySource implements DisplaySource {

    public static final String TYPE = "raw";

    private URI uri;

    public RawDisplaySource(RawDisplaySource copy) {
        this.uri = copy.uri;
    }

    public RawDisplaySource() { }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public URI getUri() {
        return this.uri;
    }

    @Override
    public void resetUri() {

    }

    @Override
    public String getStatus() {
        return this.uri.toString();
    }

    @Override
    public void writeNbt(CompoundTag nbt) {
        if (this.uri != null) {
            nbt.putString("url", this.uri.toString());
        }
    }

    @Override
    public void readNbt(CompoundTag nbt) {
        if (nbt.get("url") instanceof StringTag nbtRaw) {
            try {
                this.uri = URI.create(nbtRaw.getAsString());
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

}
