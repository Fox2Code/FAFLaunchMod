package com.fox2code.faflaunchmod.loader.mixin;

import com.bawnorton.mixinsquared.MixinSquaredBootstrap;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.util.HashSet;

public class ModLoaderMixin {
    private static final HashSet<String> configurations = new HashSet<>();
    private static boolean preInitialized = false, initialized = false;
    public static IMixinTransformer init() {
        if (preInitialized) throw new IllegalStateException("Duplicate call to initializeMixin");
        System.setProperty("mixin.bootstrapService", MixinBootstrapService.class.getName());
        System.setProperty("mixin.service", MixinService.class.getName());
        MixinBootstrap.init();
        MixinEnvironment.getCurrentEnvironment()
                .setOption(MixinEnvironment.Option.DISABLE_REFMAP, true);
        MixinEnvironment.getCurrentEnvironment()
                .setOption(MixinEnvironment.Option.DEBUG_INJECTORS, true);
        MixinEnvironment.getCurrentEnvironment()
                .setOption(MixinEnvironment.Option.DEBUG_VERBOSE, true);
        for (MixinEnvironment.Phase phase : new MixinEnvironment.Phase[]{
                MixinEnvironment.Phase.PREINIT, MixinEnvironment.Phase.INIT, MixinEnvironment.Phase.DEFAULT}) {
            MixinEnvironment.getEnvironment(phase).setSide(MixinEnvironment.Side.UNKNOWN);
        }
        MixinBootstrap.getPlatform().inject();
        IMixinTransformer mixinTransformer = // Inject mixin transformer into class loader.
                (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        MixinExtrasBootstrap.init();
        MixinSquaredBootstrap.init();
        preInitialized = true;
        return mixinTransformer;
    }

    public static void notifyInitialized() {
        if (initialized) throw new IllegalStateException("Duplicate call to notifyInitialized!");
        if (!preInitialized) throw new IllegalStateException("Mixins are not pre initialized!");
        ((MixinService) org.spongepowered.asm.service.MixinService.getService()).onStartup();
        initialized = true;
    }

    public static void addMixinConfigurationSafe(String modId, String mixin) {
        if (!initialized) {
            throw new IllegalStateException("Trying to use Mixin service before it has been initialized");
        }
        if (!configurations.add(mixin)) {
            throw new IllegalArgumentException("Mixin " + mixin + " has already been registered!");
        }
        Mixins.addConfiguration(mixin);
        Mixins.getConfigs().stream().filter(config1 ->
                config1.getName().equals(mixin)).findFirst().ifPresent(config -> {
            config.getConfig().decorate(FabricUtil.KEY_MOD_ID, modId);
        });
    }
}
