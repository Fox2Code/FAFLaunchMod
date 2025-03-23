package com.fox2code.faflaunchmod.mixins;

import com.fox2code.faflaunchmod.event.ApplicationInitEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.springframework.context.ConfigurableApplicationContext;

@Mixin(targets = "com.faforever.client.FafClientApplication")
public class MixinFafClientApplication {
    @Shadow
    private ConfigurableApplicationContext applicationContext;

    @Inject(method = "init()V",
            at = @At("RETURN"), remap = false)
    public void onInitialized(CallbackInfo callbackInfo) {
        new ApplicationInitEvent(this.applicationContext).callEvent();
    }
}
