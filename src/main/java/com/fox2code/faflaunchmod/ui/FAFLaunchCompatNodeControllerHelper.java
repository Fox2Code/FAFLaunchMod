package com.fox2code.faflaunchmod.ui;

final class FAFLaunchCompatNodeControllerHelper {
    private FAFLaunchCompatNodeControllerHelper() {}

    interface FAFLaunchCompatController<ROOT> {
        ROOT getRoot();
    }
}
