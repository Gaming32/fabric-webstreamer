package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.theorozier.webstreamer.WebStreamer;
import fr.theorozier.webstreamer.display.url.DisplayUrl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Vec3i;
import org.lwjgl.opengl.GL11;

@Environment(EnvType.CLIENT)
public abstract class DisplayLayer {

    /** The timeout for a layer to be considered unused */
    protected static final long LAYER_UNUSED_TIMEOUT = 15L * 1000000000L;

    // Common //
    protected final DisplayUrl url;
    protected final DisplayLayerResources res;
    protected final DisplayTexture tex;
    protected final DisplayRenderLayer renderLayer;

    // Timing //
    /** Time in nanoseconds (monotonic) of the last use. */
    protected long lastUse = 0;

    public DisplayLayer(DisplayUrl url, DisplayLayerResources res) {
        this.url = url;
        this.res = res;
        this.tex = new DisplayTexture();
        this.renderLayer = new DisplayRenderLayer(this);
        WebStreamer.LOGGER.info(makeLog("Allocate display layer for {}"), this.url);
    }

    /** Called when the display layer is being freed, before garbage collection. */
    protected void free() {
        WebStreamer.LOGGER.info(makeLog("Free display layer for {}"), this.url);
        this.tex.releaseId();
    }

    /** Called on each reader tick. */
    protected abstract void tick();

    /** Called for each display position and configuration. */
    @SuppressWarnings("unused")
    public void pushAudioSource(Vec3i pos, float dist, float audioDistance, float audioVolume) { }

    /**
     * Check if this layer is unused for too long, in such case
     * @param now The reference timestamp (monotonic nanoseconds from {@link System#nanoTime()}).
     * @return True if the layer is unused for too long.
     */
    public boolean isUnused(long now) {
        return now - this.lastUse >= LAYER_UNUSED_TIMEOUT;
    }

    /**
     * @return True if this layer should be lost while currently used.
     */
    public boolean isLost() {
        return false;
    }

    protected String makeLog(String message) {
        return String.format("[%s:%08X] ", this.getClass().getSimpleName(), this.url.uri().hashCode()) + message;
    }

    public RenderType getRenderLayer() {
        return this.renderLayer;
    }

    private static class DisplayRenderLayer extends RenderType {
        private DisplayRenderLayer(DisplayLayer layer) {
            super("display", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                    256, false, true,
                    () -> {
                        layer.lastUse = System.nanoTime();
                        POSITION_TEX_SHADER.setupRenderState();
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(GL11.GL_LEQUAL);
                        RenderSystem.enableTexture();
                        RenderSystem.setShaderTexture(0, layer.tex.getId());
                    },
                    RenderSystem::disableDepthTest);
        }
    }

}
