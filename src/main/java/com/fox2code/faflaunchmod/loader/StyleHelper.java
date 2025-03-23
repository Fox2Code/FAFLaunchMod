package com.fox2code.faflaunchmod.loader;

import com.fox2code.faflaunchmod.launcher.Main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

public final class StyleHelper {
    public static final String STYLE_EXTENSIONS_PATH = "theme/faflaunchmod.styles.css";
    private static final ArrayList<String> styles = new ArrayList<>();

    static void addStyle(String source) {
        if (!styles.contains(source)) {
            styles.add(source);
        }
    }

    static byte[] patchStyleExtensions(byte[] bytes) throws IOException {
        if (styles.isEmpty()) return bytes;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.writeBytes(bytes);
        for (String style : styles) {
            URL resource = Main.getLaunchClassLoader().findResource(style);
            if (resource != null) {
                try (InputStream inputStream = resource.openStream()) {
                    byteArrayOutputStream.writeBytes(inputStream.readAllBytes());
                    byteArrayOutputStream.write('\n');
                }
            }
        }
        return byteArrayOutputStream.toByteArray();
    }
}
