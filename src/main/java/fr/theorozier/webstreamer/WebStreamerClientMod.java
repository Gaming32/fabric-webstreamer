package fr.theorozier.webstreamer;

import fr.theorozier.webstreamer.display.render.DisplayBlockEntityRenderer;
import fr.theorozier.webstreamer.display.render.DisplayLayerManager;
import fr.theorozier.webstreamer.display.url.DisplayUrlManager;
import fr.theorozier.webstreamer.twitch.TwitchClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.RenderType;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

@Environment(EnvType.CLIENT)
public class WebStreamerClientMod implements ClientModInitializer {

    private static final String MAVEN_URL = "https://repo.maven.apache.org/maven2/";

    public static DisplayUrlManager DISPLAY_URLS;
    public static DisplayLayerManager DISPLAY_LAYERS;
    public static TwitchClient TWITCH_CLIENT;

    private static final List<Path> LIB_ROOTS = new ArrayList<>();

    @Override
    public void onInitializeClient() {

        BlockEntityRendererRegistry.register(WebStreamerMod.DISPLAY_BLOCK_ENTITY, DisplayBlockEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(WebStreamerMod.DISPLAY_BLOCK, RenderType.cutout());

        System.setProperty("org.bytedeco.javacpp.logger", "slf4j");

        final String javacppVersion;
        try {
            javacppVersion = Loader.getVersion();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        WebStreamerMod.LOGGER.info("JavaCPP version: {}", javacppVersion);

        LIB_ROOTS.add(getLibRoot(downloadArtifact(
            "org.bytedeco", "javacpp", javacppVersion, Loader.getPlatform()
        )));
        LIB_ROOTS.add(getLibRoot(downloadArtifact(
            "org.bytedeco", "ffmpeg", "6.0-" + javacppVersion, Loader.getPlatform()
        )));

        DISPLAY_URLS = new DisplayUrlManager();
        DISPLAY_LAYERS = new DisplayLayerManager();
        TWITCH_CLIENT = new TwitchClient();

        FFmpegLogCallback.setLevel(avutil.AV_LOG_ERROR);

    }

    public static Enumeration<URL> getResources(String path, @Nullable Class<?> owner) {
        if (path.contains("drm")) {
            // Hack to fix weird libdrm conflict with GLFW on non-NVIDIA graphics cards
            return Collections.emptyEnumeration();
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        } else if (owner != null) {
            path = owner.getPackageName().replace('.', '/') + '/' + path;
        }
        final String fpath = path;
        final Iterator<URL> itResult = LIB_ROOTS.stream()
            .map(root -> {
                final Path resultPath = root.resolve(fpath);
                if (Files.exists(resultPath)) {
                    try {
                        return resultPath.toUri().toURL();
                    } catch (MalformedURLException e) {
                        WebStreamerMod.LOGGER.warn("Failed to convert path {} to URL", resultPath, e);
                    }
                }
                return null;
            })
            .filter(Objects::nonNull)
            .iterator();
        return new Enumeration<>() {
            @Override
            public boolean hasMoreElements() {
                return itResult.hasNext();
            }

            @Override
            public URL nextElement() {
                return itResult.next();
            }
        };
    }

    @Nullable
    public static URL getResource(String path, @Nullable Class<?> owner) {
        final Enumeration<URL> result = getResources(path, owner);
        return result.hasMoreElements() ? result.nextElement() : null;
    }

    @Nullable
    public static InputStream getResourceAsStream(String path, @Nullable Class<?> owner) {
        final URL resource = getResource(path, owner);
        if (resource == null) {
            return null;
        }
        try {
            return resource.openStream();
        } catch (IOException e) {
            return null;
        }
    }

    private static Path getLibRoot(Path fsPath) {
        try {
            return FileSystems.getFileSystem(URI.create("jar:" + fsPath.toUri() + "!/")).getRootDirectories().iterator().next();
        } catch (FileSystemNotFoundException e) {
            try {
                return FileSystems.newFileSystem(fsPath).getRootDirectories().iterator().next();
            } catch (IOException e1) {
                throw new UncheckedIOException(e1);
            }
        }
    }

    private static Path downloadArtifact(
        String group,
        String artifact,
        String version,
        @Nullable String classifier
    ) {
        final String prettyName = group + ':' + artifact + ':' + version + (classifier != null ? ':' + classifier : "");
        final String mavenPath = group.replace('.', '/') + '/' +
            artifact + '/' +
            version + '/' +
            artifact + '-' + version + (classifier != null ? '-' + classifier : "") + ".jar";
        final Path destPath = Path.of(System.getProperty("user.home")).resolve(".m2/repository").resolve(mavenPath);
        if (Files.isRegularFile(destPath)) {
            WebStreamerMod.LOGGER.info("Skipping download of {}, as it already exists in Maven Local", prettyName);
            return destPath;
        }
        WebStreamerMod.LOGGER.info("Downloading {} from Maven Central", prettyName);
        try {
            Files.createDirectories(destPath.getParent());
            final URL downloadUrl = new URL(MAVEN_URL + mavenPath);
            try (InputStream is = downloadUrl.openStream()) {
                Files.copy(is, destPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            WebStreamerMod.LOGGER.error("Failed to download {} from Maven Central", prettyName, e);
            final RuntimeException throwException = e instanceof RuntimeException re ? re : new RuntimeException(e);
            try {
                Files.delete(destPath);
            } catch (IOException e1) {
                throwException.addSuppressed(e1);
            }
            throw throwException;
        }
        WebStreamerMod.LOGGER.info("Downloaded {} from Maven Central", prettyName);
        return destPath;
    }

}
