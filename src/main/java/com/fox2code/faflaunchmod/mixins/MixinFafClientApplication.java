package com.fox2code.faflaunchmod.mixins;

import com.fox2code.faflaunchmod.event.ApplicationInitEvent;
import com.fox2code.faflaunchmod.event.MainWindowShowedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ch.micheljung.fxwindow.FxStage;

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

    @Inject(method = "showMainWindow(Lch/micheljung/fxwindow/FxStage;)V",
            at = @At("RETURN"), remap = false)
    public void onShowMainWindow(FxStage fxStage, CallbackInfo callbackInfo) {
        new MainWindowShowedEvent(this.applicationContext, fxStage).callEvent();
    }
}
