package com.fox2code.faflaunchmod.utils;

import java.lang.instrument.Instrumentation;

public final class AgentHelper {
    public static Instrumentation instrumentation;

    private AgentHelper() {}

    public static void premain(final String agentArgs, final Instrumentation instrumentation) {
        if (AgentHelper.instrumentation == null && instrumentation != null) {
            AgentHelper.instrumentation = instrumentation;
        }
    }

    public static void agentmain(final String agentArgs, final Instrumentation instrumentation) {
        if (AgentHelper.instrumentation == null && instrumentation != null) {
            AgentHelper.instrumentation = instrumentation;
        }
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
