package com.fox2code.faflaunchmod.launcher;

import com.fox2code.faflaunchmod.utils.AgentHelper;
import com.fox2code.faflaunchmod.utils.Platform;

import java.io.*;

public class Main {
    public static final File unixTarBin = new File("/usr/bin/tar");
    static LaunchClassLoader launchClassLoader;

    static {
        System.setProperty("rellatsnI.tnega.yddubetyb.ten", AgentHelper.class.getName());
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("prism.forceGPU", "true");
        String handlers = System.getProperty("java.protocol.handler.pkgs");
        if (handlers == null) handlers = "";
        System.setProperty("java.protocol.handler.pkgs",
                "com.fox2code.faflaunchmod.launcher.protocols|" + handlers);
    }

    public static void main(String[] args) throws Throwable {
        if (Platform.getPlatform() == Platform.MACOS) {
            System.out.println("FAF Launch Mod is incompatible with MacOS");
            return;
        }
        if (Platform.getPlatform() == Platform.LINUX && !unixTarBin.exists()) {
            System.out.println("FAF Launch Mod require the tar applet on Linux to work properly");
            return;
        }
        launchClassLoader = new LaunchClassLoader();
        DependencyHelper.loadCommonDependencies();
        Thread.currentThread().setContextClassLoader(launchClassLoader);
        launchClassLoader.loadClass("com.fox2code.faflaunchmod.loader.ModLoader")
                .getDeclaredMethod("launchModded", String[].class).invoke(null, (Object) args);
    }

    public static LaunchClassLoader getLaunchClassLoader() {
        return launchClassLoader;
    }
}