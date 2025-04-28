package com.fox2code.faflaunchmod.event;

import com.fox2code.foxevents.Event;
import ch.micheljung.fxwindow.FxStage;
import org.springframework.context.ConfigurableApplicationContext;

public final class MainWindowShowedEvent extends Event {
    private final ConfigurableApplicationContext applicationContext;
    private final FxStage fxStage;

    public MainWindowShowedEvent(ConfigurableApplicationContext applicationContext, FxStage fxStage) {
        this.applicationContext = applicationContext;
        this.fxStage = fxStage;
    }

    public ConfigurableApplicationContext getApplicationContext() {
        return this.applicationContext;
    }

    public FxStage getFxStage() {
        return this.fxStage;
    }
}
