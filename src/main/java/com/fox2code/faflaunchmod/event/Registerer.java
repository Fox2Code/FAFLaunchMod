package com.fox2code.faflaunchmod.event;

import com.fox2code.faflaunchmod.launcher.Main;
import com.fox2code.foxevents.EventCallback;
import com.fox2code.foxevents.FoxEvents;
import io.github.karlatemp.unsafeaccessor.Root;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.util.function.BooleanSupplier;

public final class Registerer extends FoxEvents {
    private static final MethodHandles.Lookup TRUSTED_LOOKUP = Root.getTrusted(null);
    public static Registerer INSTANCE = new Registerer();

    static {
        FoxEvents.setFoxEvents(INSTANCE);
    }

    private Registerer() {}

    @Override
    public void registerEvents(@NotNull Object handler) {
        if (handler.getClass().getClassLoader() != Main.getLaunchClassLoader()) {
            throw new IllegalArgumentException("Handler doesn't use the same class loader as FAFLaunchMod");
        }
        for (EventCallback eventCallback : this.getEventCallbacks(handler, null, false, TRUSTED_LOOKUP)) {
            this.registerEventCallback(eventCallback);
        }
    }

    @Override
    public void registerEvents(@NotNull Object handler,@Nullable BooleanSupplier validator) {
        if (handler.getClass().getClassLoader() != Main.getLaunchClassLoader()) {
            throw new IllegalArgumentException("Handler doesn't use the same class loader as FAFLaunchMod");
        }
        for (EventCallback eventCallback : this.getEventCallbacks(handler, validator, false, TRUSTED_LOOKUP)) {
            this.registerEventCallback(eventCallback);
        }
    }

    @Override
    public void unregisterEvents(@NotNull Object handler) {
        if (handler.getClass().getClassLoader() != Main.getLaunchClassLoader()) {
            throw new IllegalArgumentException("Handler doesn't use the same class loader as FAFLaunchMod");
        }
        this.unregisterEventsForClassLoader(FoxEvents.class.getClassLoader(), handler);
    }

    public void check() {}
}
