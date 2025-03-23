package com.fox2code.faflaunchmod.ui;

import javafx.scene.Node;

public interface FAFLaunchCompatNodeController<ROOT extends Node> extends
        FAFLaunchCompatNodeControllerHelper.FAFLaunchCompatController<ROOT> {
    ROOT getRoot();
}
