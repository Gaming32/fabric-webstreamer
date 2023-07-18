package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.WebStreamer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Different pool types given to {@link DisplayLayerHls} as a centralized way of getting
 * access to heavy heap buffers. This also provides a thread pool executor and an HTTP
 * client in order to reduce overhead when creating them.
 */
@Environment(EnvType.CLIENT)
public class DisplayLayerResources {

    /** 8 Kio buffer for converting (16 or 8 bits) stereo to mono 16 bits audio stream. */
    private static final int AUDIO_BUFFER_SIZE = 8192;
    /** Limit to 512 Kio of audio buffers. */
    private static final int AUDIO_BUFFER_LIMIT = 64;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();
        @Override
        public Thread newThread(@NotNull Runnable r) {
            return new Thread(r, "WebStreamer Display Queue (" + this.counter.getAndIncrement() + ")");
        }
    });

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final List<ShortBuffer> audioBuffers = new ArrayList<>();

    private int audioBuffersCount = 0;

    public ExecutorService getExecutor() {
        return this.executor;
    }

    public HttpClient getHttpClient() {
        return this.httpClient;
    }

    /**
     * Allocate a sound buffer. Such buffers are backed by a native memory in
     * order to be directly used as OpenAL buffer data.
     */
    public ShortBuffer allocAudioBuffer() {
        synchronized (this.audioBuffers) {
            try {
                return this.audioBuffers.remove(this.audioBuffers.size() - 1);
            } catch (IndexOutOfBoundsException e) {
                if (this.audioBuffersCount >= AUDIO_BUFFER_LIMIT) {
                    throw new IllegalStateException("reached maximum number of allocated audio buffers: " + AUDIO_BUFFER_LIMIT);
                }
                this.audioBuffersCount++;
                WebStreamer.LOGGER.debug("Number of allocated sound buffers: {}", this.audioBuffersCount);
                return ByteBuffer.allocateDirect(AUDIO_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            }
        }
    }

    public void freeAudioBuffer(ShortBuffer buffer) {
        synchronized (this.audioBuffers) {
            this.audioBuffers.add(buffer);
        }
    }

}
