package com.fox2code.faflaunchmod.mixins;

import io.github.sheikah45.fx2j.api.Fx2jLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.net.URL;

@Mixin(Fx2jLoader.class)
public class MixinFx2jLoader {
    @Shadow
    private URL location;

    @Inject(method = "load()Ljava/lang/Object;",
            at = @At("HEAD"), cancellable = true, remap = false)
    public <T> void loadHook(CallbackInfoReturnable<T> cir) throws IOException {
        // Force reading from fxml with flm URLs or else our changes won't load.
        if (this.location != null && this.location.getProtocol().equals("flm")) {
            cir.setReturnValue(loadFromFxml());
        }
    }

    @Shadow
    private <T> T loadFromFxml() throws IOException {
        return null;
    }
}
