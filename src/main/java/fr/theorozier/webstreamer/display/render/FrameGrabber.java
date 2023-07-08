package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.display.audio.AudioStreamingBuffer;
import fr.theorozier.webstreamer.util.NamedInputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.io.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ShortBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.function.Consumer;

/**
 * <p>A custom FFMPEG frame grabber working with image frames priority, this means that multiple
 * audio frames can be grabbed before finding the right image frame for the right timestamp.</p>
 * <p>The fact that FFMPEG will return the same {@link Frame} instance on every call requires us
 * to "bufferize" audio frames between each grab.</p>
 */
@Environment(EnvType.CLIENT)
public class FrameGrabber {

    private final DisplayLayerResources pools;
    private final URI uri;
    private final String name;
    private final byte[] initBytes;

    private FFmpegFrameGrabber grabber;
    private long refTimestamp;
    private long deltaTimestamp;
    private Frame lastFrame;

    private ShortBuffer tempAudioBuffer;

    private ArrayDeque<AudioStreamingBuffer> startAudioBuffers;

    public FrameGrabber(DisplayLayerResources pools, URI uri, String name, byte[] initBytes) {
        this.pools = pools;
        this.uri = uri;
        this.name = name;
        this.initBytes = initBytes;
    }

    public void start() throws IOException {

        if (this.grabber != null) {
            throw new IllegalStateException("already started");
        }

        try {

            HttpRequest req = HttpRequest.newBuilder(this.uri).GET().timeout(Duration.ofSeconds(1)).build();
            final byte[] buffer = this.pools.getHttpClient().send(req, HttpResponse.BodyHandlers.ofByteArray()).body();

            InputStream inputStream = new ByteArrayInputStream(buffer);
            if (initBytes != null) {
                inputStream = new SequenceInputStream(new ByteArrayInputStream(initBytes), inputStream);
            }
            if (!inputStream.markSupported()) {
                inputStream = new BufferedInputStream(inputStream);
            }
            inputStream = new NamedInputStream(inputStream, name);

            this.grabber = new FFmpegFrameGrabber(inputStream);
            this.grabber.startUnsafe();

            this.tempAudioBuffer = this.pools.allocAudioBuffer();

            this.refTimestamp = 0L;
            this.deltaTimestamp = 0L;
            this.lastFrame = null;

            this.startAudioBuffers = new ArrayDeque<>();

            Frame frame;
            while ((frame = this.grabber.grab()) != null) {
                if (frame.image != null) {
                    this.refTimestamp = frame.timestamp;
                    this.lastFrame = frame;
                    break;
                } else if (frame.samples != null) {
                    this.startAudioBuffers.addLast(AudioStreamingBuffer.fromFrame(this.tempAudioBuffer, frame));
                }
            }

        } catch (IOException | InterruptedException | RuntimeException e) {

            if (this.grabber != null) {
                this.grabber.releaseUnsafe();
            }

            if (this.tempAudioBuffer != null) {
                this.pools.freeAudioBuffer(this.tempAudioBuffer);
                this.tempAudioBuffer = null;
            }

            if (e instanceof InterruptedException) {
                throw new IOException(e);
            } else if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw (RuntimeException) e;
            }

        }

    }

    public void stop() {

        if (this.grabber == null || this.tempAudioBuffer == null) {
            throw new IllegalStateException("Frame grabber is not started.");
        }

        try {
            this.grabber.releaseUnsafe();
        } catch (IOException ignored) { }

        this.pools.freeAudioBuffer(this.tempAudioBuffer);

        this.grabber = null;
        this.tempAudioBuffer = null;

        if (this.startAudioBuffers != null) {
            this.startAudioBuffers.forEach(AudioStreamingBuffer::free);
            this.startAudioBuffers = null;
        }

    }

    /**
     * Grab the image frame at the corresponding timestamp, the grabber will attempt
     * to get the closest frame before timestamp.
     * @param timestamp The relative timestamp in microseconds. Relative to the first image frame
     * @param audioBufferConsumer A consumer for audio buffers decoded during image frame selection.
     * @return The grabbed frame or null if frame has not updated since last grab.
     */
    public Frame grabAt(long timestamp, Consumer<AudioStreamingBuffer> audioBufferConsumer) throws IOException {

        if (this.startAudioBuffers != null) {
            // Called once after start with audio buffers placed before the first frame.
            this.startAudioBuffers.forEach(audioBufferConsumer);
            this.startAudioBuffers = null;
        }

        long realTimestamp = timestamp + this.refTimestamp;

        if (this.lastFrame != null) {
            if (this.lastFrame.timestamp <= realTimestamp) {
                Frame frame = this.lastFrame;
                this.lastFrame = null;
                return frame;
            } else {
                return null;
            }
        }

        Frame frame;
        while ((frame = this.grabber.grab()) != null) {
            if (frame.image != null) {

                if (this.deltaTimestamp == 0) {
                    this.deltaTimestamp = frame.timestamp - this.refTimestamp;
                }

                if (frame.timestamp <= realTimestamp) {
                    // Delta of the current frame with the targeted timestamp
                    long delta = realTimestamp - frame.timestamp;
                    if (delta <= this.deltaTimestamp) {
                        return frame;
                    }
                } else {
                    this.lastFrame = frame;
                    break;
                }

            } else if (frame.samples != null) {
                audioBufferConsumer.accept(AudioStreamingBuffer.fromFrame(this.tempAudioBuffer, frame));
            }
        }

        return null;

    }

    public void grabRemaining(Consumer<AudioStreamingBuffer> audioBufferConsumer) throws IOException {
        Frame frame;
        while ((frame = this.grabber.grab()) != null) {
            if (frame.samples != null) {
                audioBufferConsumer.accept(AudioStreamingBuffer.fromFrame(this.tempAudioBuffer, frame));
            }
        }
    }

}
