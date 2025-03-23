package com.fox2code.faflaunchmod.utils;

import java.io.File;
import java.util.Locale;

public enum Platform {
    WINDOWS("start", "\\bin\\java.exe"),
    MACOS("open", "/bin/java"),
    LINUX("xdg-open", "/bin/java");

    private static final Platform platform;
    private static final File fafDirectory;

    static {
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (name.startsWith("win")) {
            platform = WINDOWS;
        } else if (name.startsWith("mac") ||
                name.startsWith("darwin")) {
            platform = MACOS;
        } else if (name.contains("nix") ||
                name.contains("nux") ||
                name.contains("aix")) {
            platform = LINUX;
        } else {
            throw new Error("Unsupported system");
        }
        fafDirectory = getAppDir((platform == WINDOWS ?
                "Forged Alliance Forever" : ".faforever"));
    }

    public final String open;
    public final File javaBin;

    Platform(String open, String javaBin) {
        this.open = open;
        this.javaBin = new File(System.getProperty("java.home") + javaBin).getAbsoluteFile();
    }

    public static Platform getPlatform() {
        return platform;
    }

    public static File getAppDir(String baseDir) {
        String homeDir = System.getProperty("user.home", ".");
        File file;
        if (platform == Platform.WINDOWS) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                file = new File(appdata, "." + baseDir + '/');
            } else {
                file = new File(homeDir, '.' + baseDir + '/');
            }
        } else {
            file = new File(homeDir, baseDir + '/');
        }

        if (!file.exists() && !file.mkdirs()) {
            throw new RuntimeException("The working directory could not be created: " + file);
        } else {
            return file.getAbsoluteFile();
        }
    }

    public static File getFafDirectory() {
        return fafDirectory;
    }
}
