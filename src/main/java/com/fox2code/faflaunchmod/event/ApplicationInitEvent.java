package com.fox2code.faflaunchmod.event;

import com.fox2code.foxevents.Event;
import org.springframework.context.ConfigurableApplicationContext;

public final class ApplicationInitEvent extends Event {
    private final ConfigurableApplicationContext applicationContext;

    public ApplicationInitEvent(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public ConfigurableApplicationContext getApplicationContext() {
        return this.applicationContext;
    }
}
