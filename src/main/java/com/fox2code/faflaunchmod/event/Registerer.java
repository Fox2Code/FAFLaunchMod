package com.fox2code.faflaunchmod.event;

import com.fox2code.faflaunchmod.launcher.Main;
import com.fox2code.foxevents.EventCallback;
import com.fox2code.foxevents.FoxEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public final class Registerer extends FoxEvents {
    public static Registerer INSTANCE = new Registerer();

    static {
        FoxEvents.setFoxEvents(INSTANCE);
    }

    private Registerer() {}

    @Override
    public void registerEvents(@NotNull Object handler) {
        if (handler.getClass().getClassLoader() != Main.getLaunchClassLoader()) {
            throw new IllegalArgumentException("Handler doesn't use same class loader as FAFLaunchMod");
        }
        for (EventCallback eventCallback : this.getEventCallbacks(handler)) {
            this.registerEventCallback(eventCallback);
        }
    }

    @Override
    public void registerEvents(@NotNull Object handler,@Nullable BooleanSupplier validator) {
        if (handler.getClass().getClassLoader() != Main.getLaunchClassLoader()) {
            throw new IllegalArgumentException("Handler doesn't use same class loader as FAFLaunchMod");
        }
        for (EventCallback eventCallback : this.getEventCallbacks(handler, validator)) {
            this.registerEventCallback(eventCallback);
        }
    }

    @Override
    public void unregisterEvents(@NotNull Object handler) {
        if (handler.getClass().getClassLoader() != Main.getLaunchClassLoader()) {
            throw new IllegalArgumentException("Handler doesn't use same class loader as FAFLaunchMod");
        }
        this.unregisterEventsForClassLoader(FoxEvents.class.getClassLoader(), handler);
    }

    public void check() {}
}
