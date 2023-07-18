package fr.theorozier.webstreamer.display.source;

import fr.theorozier.webstreamer.WebStreamer;
import fr.theorozier.webstreamer.WebStreamerClient;
import fr.theorozier.webstreamer.playlist.Playlist;
import fr.theorozier.webstreamer.playlist.PlaylistQuality;
import fr.theorozier.webstreamer.twitch.TwitchClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;

import java.net.URI;

public class TwitchDisplaySource implements DisplaySource {

    public static final String TYPE = "twitch";

    private String channel;
    private String quality;
    // private PlaylistQuality quality;

    public TwitchDisplaySource(TwitchDisplaySource copy) {
        this.channel = copy.channel;
        this.quality = copy.quality;
        // this.quality = copy.quality;
    }

    public TwitchDisplaySource() { }

    public void setChannelQuality(String channel, String quality) {
        this.channel = channel;
        this.quality = quality;
    }

    public void clearChannelQuality() {
        this.channel = null;
        this.quality = null;
    }

    public String getChannel() {
        return channel;
    }

    public String getQuality() {
        return quality;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public URI getUri() {
        if (this.channel != null && this.quality != null) {
            try {
                Playlist playlist = WebStreamerClient.TWITCH_CLIENT.requestPlaylist(this.channel);
                PlaylistQuality quality = playlist.getQuality(this.quality);
                return quality.uri();
            } catch (TwitchClient.PlaylistException e) {
                WebStreamer.LOGGER.error("Failed to request twitch channel", e);
            }
        }
        return null;
    }

    @Override
    public void resetUri() {
        if (this.channel != null) {
            WebStreamerClient.TWITCH_CLIENT.forgetPlaylist(this.channel);
            WebStreamer.LOGGER.info("Forget twitch playlist for channel " + this.channel);
        }
    }

    @Override
    public String getStatus() {
        return this.channel + " / " + this.quality;
    }

    @Override
    public void writeNbt(CompoundTag nbt) {
        if (this.channel != null && this.quality != null) {
            nbt.putString("channel", this.channel);
            nbt.putString("quality", this.quality);
            //nbt.putString("url", this.quality.uri().toString());
        }
    }

    @Override
    public void readNbt(CompoundTag nbt) {
        if (
            nbt.get("channel") instanceof StringTag channel &&
            nbt.get("quality") instanceof StringTag quality /*&&
            nbt.get("url") instanceof NbtString urlRaw*/
        ) {
            try {
                // URI uri = URI.create(urlRaw.asString());
                this.channel = channel.getAsString();
                this.quality = quality.getAsString();
                // his.quality = new PlaylistQuality(quality.asString(), uri);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

}
