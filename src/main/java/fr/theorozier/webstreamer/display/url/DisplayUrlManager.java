package fr.theorozier.webstreamer.display.url;

import fr.theorozier.webstreamer.WebStreamer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class DisplayUrlManager {

    private final Map<URI, DisplayUrl> urlCache = new HashMap<>();

    public DisplayUrl allocUri(URI uri) {
        return urlCache.computeIfAbsent(uri, key -> {
            final DisplayUrl result = new DisplayUrl(key, urlCache.size() + 1);
            WebStreamer.LOGGER.info("Allocated a new display url {}.", result);
            return result;
        });
    }

}
