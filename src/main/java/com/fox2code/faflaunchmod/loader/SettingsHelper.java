package com.fox2code.faflaunchmod.loader;

import com.fox2code.faflaunchmod.launcher.Main;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public final class SettingsHelper {
    private static final boolean DEBUG = false;
    public static String SETTINGS_PATH = "theme/settings/settings.fxml";
    public static String FAF_LAUNCH_MOD_SETTINGS_PATH = "theme/settings/faflaunchmod.settings.fxml";
    private static final ArrayList<String> settingsPanels = new ArrayList<>();
    private static final String ADDITION = """
        
                        <Tab>
                            <content>
                                <ScrollPane fitToWidth="true" maxHeight="1.7976931348623157E308"
                                            maxWidth="1.7976931348623157E308">
                                    <content>
                                        <fx:include source="faflaunchmod.settings.fxml"/>
                                    </content>
                                </ScrollPane>
                            </content>
                            <graphic>
                                <Group>
                                    <children>
                                        <VBox prefWidth="200.0" rotate="90.0">
                                            <children>
                                                <Label styleClass="tab-label" text="%faflaunchmod.name"/>
                                            </children>
                                        </VBox>
                                    </children>
                                </Group>
                            </graphic>
                        </Tab>
        """;

    static void init() {
        Main.getLaunchClassLoader().addLoaderResourceLoadingPatch(SETTINGS_PATH);
        Main.getLaunchClassLoader().addLoaderResourceLoadingPatch(FAF_LAUNCH_MOD_SETTINGS_PATH);
    }

    static void addSettingPanel(String path) {
        URL resource = Main.getLaunchClassLoader().getResource(path);
        if (resource != null && !settingsPanels.contains(path)) {
            settingsPanels.add(path);
        }
    }

    static byte[] patchSettingsFxml(byte[] data) {
        String settingsFxml = new String(data, StandardCharsets.UTF_8);
        if (DEBUG) {
            System.out.println("============================================================================================");
            System.out.println(settingsFxml);
            System.out.println("============================================================================================");
            // settingsFxml = settingsFxml.replace("%settings.data", "%faflaunchmod.name");
        }
        int lastIndex = settingsFxml.lastIndexOf("</Tab>");
        String result = (settingsFxml.substring(0, lastIndex + 7) +
                ADDITION + settingsFxml.substring(lastIndex + 7));
        if (DEBUG) {
            System.out.println("============================================================================================");
            System.out.println(result);
            System.out.println("============================================================================================");
            new Throwable().printStackTrace(System.out);
        }
        return result.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] patchFAFLaunchModSettingsFxml(byte[] data) {
        String settingsFxml = new String(data, StandardCharsets.UTF_8);
        int lastIndex = settingsFxml.lastIndexOf("<children>");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(settingsFxml, 0, lastIndex + 10);
        for (String settingPanel : settingsPanels) {
            stringBuilder.append("\n        <fx:include source=\"/")
                    .append(settingPanel).append("\"/>");
        }
        stringBuilder.append(settingsFxml, lastIndex + 10, settingsFxml.length());
        return stringBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
