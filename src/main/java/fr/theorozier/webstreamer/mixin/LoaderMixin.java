package fr.theorozier.webstreamer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.bytedeco.javacpp.Loader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.net.URL;

@Mixin(value = Loader.class, remap = false)
public class LoaderMixin {
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
}
