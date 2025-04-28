package com.fox2code.faflaunchmod.utils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class DesktopUtils {
    private static final boolean supportDesktop = Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
    private static final boolean useDesktopForFile = supportDesktop &&
            Platform.getPlatform() != Platform.LINUX;

    public static void openFolder(File file) {
        // openFolder() ensure destination is a folder for security purposes
        if (!file.isDirectory()) return;
        openFile(file);
    }

    public static void openFile(File file) {
        if (!file.exists()) return;
        if (useDesktopForFile) {
            try {
                openURL(file.toURI().toURL());
            } catch (MalformedURLException e) {
                openPathRuntimeImpl(file.getAbsolutePath());
            }
        } else {
            openPathRuntimeImpl(file.getAbsolutePath());
        }
    }

    public static void openURL(String url) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return;
        }
        if (supportDesktop) {
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (IOException e) {
                openPathRuntimeImpl(url);
            }
        } else {
            openPathRuntimeImpl(url);
        }
    }

    public static void openURL(URL url) {
        String protocol = url.getProtocol();
        if (!("http".equals(protocol) || "https".equals(protocol))) {
            return;
        }
        if (supportDesktop) {
            try {
                Desktop.getDesktop().browse(url.toURI());
            } catch (IOException | URISyntaxException e) {
                openPathRuntimeImpl(url.toString());
            }
        } else {
            openPathRuntimeImpl(url.toString());
        }
    }

    private static void openPathRuntimeImpl(String url) {
        try {
            new ProcessBuilder(Platform.getPlatform().open, url).start();
        } catch (IOException ignored) {}
    }
}
