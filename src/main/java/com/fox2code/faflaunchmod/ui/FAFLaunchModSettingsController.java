package com.fox2code.faflaunchmod.ui;

import com.fox2code.faflaunchmod.loader.ModLoader;
import com.fox2code.faflaunchmod.utils.DesktopUtils;
import javafx.event.ActionEvent;

import javafx.scene.layout.GridPane;
import org.springframework.stereotype.Component;

@Component
public class FAFLaunchModSettingsController implements FAFLaunchCompatNodeController<GridPane> {
    public GridPane fafLaunchModSettingsRoot;

    public void openModsFolder(ActionEvent actionEvent) {
        DesktopUtils.openFolder(ModLoader.getModsDirectory());
    }

    public final void initialize() {}

    // @Override
    public GridPane getRoot() {
        return this.fafLaunchModSettingsRoot;
    }
}
