package com.fox2code.faflaunchmod.mixins;

import com.fox2code.faflaunchmod.loader.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Pseudo
@Mixin(targets = "com.faforever.client.fa.relay.ice.IceAdapterImpl")
public class MixinIceAdapterImpl {
    // Fix ICE Adapter not working.
    @Inject(method = "startIceAdapterProcess(Ljava/nio/file/Path;Ljava/util/List;)V", at = @At("HEAD"))
    public void onStartIceAdapterProcess(Path workDirectory, List<String> cmd, CallbackInfo callbackInfo) {
        for (int i = 0; i < cmd.size(); i++) {
            String arg = cmd.get(i);
            if (arg.endsWith(".jar")) {
                arg = arg + File.pathSeparator;
            } else if (!arg.endsWith(".jar" + File.pathSeparator)) {
                continue;
            }
            cmd.set(i, arg + ModLoader.getIceAppend());
            break;
        }
    }
}
