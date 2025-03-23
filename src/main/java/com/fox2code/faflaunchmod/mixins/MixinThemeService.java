package com.fox2code.faflaunchmod.mixins;

import com.fox2code.faflaunchmod.launcher.LaunchClassLoader;
import com.fox2code.faflaunchmod.launcher.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URL;

@Mixin(targets = "com.faforever.client.theme.ThemeService")
public class MixinThemeService {
    @Inject(method = "getThemeFileUrl(Ljava/lang/String;)Ljava/net/URL;",
            at = @At("HEAD"), cancellable = true, remap = false)
    public void getThemeFileUrlHook(String path, CallbackInfoReturnable<URL> callbackInfoReturnable) {
        boolean isFLM = path.startsWith("flm:");
        if (isFLM) path = path.substring(4);
        LaunchClassLoader launchClassLoader = Main.getLaunchClassLoader();
        // Redirect loading resources to the class loader.
        URL url = path.startsWith("theme/dynamic") || isFLM ?
                launchClassLoader.findAsPatchedResource(path):
                launchClassLoader.findPatchedResource(path);
        if (url != null) {
            callbackInfoReturnable.setReturnValue(url);
        }
    }
}
