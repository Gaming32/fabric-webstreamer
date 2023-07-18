package fr.theorozier.webstreamer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.theorozier.webstreamer.WebStreamerClient;
import fr.theorozier.webstreamer.util.CompoundEnumeration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.bytedeco.javacpp.Loader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

@Environment(EnvType.CLIENT)
@Mixin(value = Loader.class, remap = false)
public class MixinLoader {
    @Unique
    private static boolean webstreamer$deleteHack;

    @WrapOperation(
        method = "extractResource(Ljava/net/URL;Ljava/io/File;Ljava/lang/String;Ljava/lang/String;Z)Ljava/io/File;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/io/File;delete()Z",
            ordinal = 1
        )
    )
    private static boolean rememberDeleteResult(File instance, Operation<Boolean> original) {
        return webstreamer$deleteHack = !original.call(instance) && instance.isFile();
    }

    @Inject(
        method = "extractResource(Ljava/net/URL;Ljava/io/File;Ljava/lang/String;Ljava/lang/String;Z)Ljava/io/File;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/io/File;delete()Z",
            ordinal = 1,
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private static void useDeleteResult(
        URL resourceURL,
        File directoryOrFile,
        String prefix,
        String suffix,
        boolean cacheDirectory,
        CallbackInfoReturnable<File> cir,
        @Local(ordinal = 1) File file
    ) {
        if (webstreamer$deleteHack) {
            cir.setReturnValue(file);
        }
    }

    @WrapOperation(
        method = "loadProperties(Ljava/lang/String;Ljava/lang/String;)Ljava/util/Properties;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Class;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"
        )
    )
    private static InputStream customResources(Class<?> instance, String name, Operation<InputStream> original) {
        final InputStream result = WebStreamerClient.getResourceAsStream(name, instance);
        return result != null ? result : original.call(instance, name);
    }

    @WrapOperation(
        method = "getVersion(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/String;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/ClassLoader;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"
        )
    )
    private static InputStream customResources(ClassLoader instance, String name, Operation<InputStream> original) {
        final InputStream result = WebStreamerClient.getResourceAsStream(name, null);
        return result != null ? result : original.call(instance, name);
    }

    @WrapOperation(
        method = "findResources(Ljava/lang/Class;Ljava/lang/String;I)[Ljava/net/URL;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Class;getResource(Ljava/lang/String;)Ljava/net/URL;"
        )
    )
    private static URL customResources1(Class<?> instance, String name, Operation<URL> original) {
        final URL result = WebStreamerClient.getResource(name, instance);
        return result != null ? result : original.call(instance, name);
    }

    @WrapOperation(
        method = "findResources(Ljava/lang/Class;Ljava/lang/String;I)[Ljava/net/URL;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/ClassLoader;getResources(Ljava/lang/String;)Ljava/util/Enumeration;"
        )
    )
    private static Enumeration<URL> customResources1(ClassLoader instance, String name, Operation<Enumeration<URL>> original) {
        return new CompoundEnumeration<>(WebStreamerClient.getResources(name, null), original.call(instance, name));
    }
}
