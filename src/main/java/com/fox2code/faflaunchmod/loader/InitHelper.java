package com.fox2code.faflaunchmod.loader;

import com.fox2code.faflaunchmod.event.Registerer;
import com.fox2code.faflaunchmod.launcher.Main;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

final class InitHelper {
    private static final ArrayList<String> initEventClasses = new ArrayList<>();

    public static void loadInit(String path) throws IOException {
        URL resource = Main.getLaunchClassLoader().findResource(path);
        if (resource == null) return;
        JsonObject initJson;
        try (InputStream inputStream = resource.openStream()) {
            initJson = ModLoader.getGson().fromJson(new InputStreamReader(
                    inputStream, StandardCharsets.UTF_8), JsonObject.class);
        }
        if (initJson.has("events")) {
            initEventClasses.add(initJson.get("events").getAsString());
        }
    }

    public static void runInit() throws Exception {
        for (String eventClass : initEventClasses) {
            Registerer.INSTANCE.registerEvents(
                    Class.forName(eventClass)
                            .getConstructor().newInstance());
        }
    }
}
