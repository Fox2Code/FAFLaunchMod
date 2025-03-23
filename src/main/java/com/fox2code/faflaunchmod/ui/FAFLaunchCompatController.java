package com.fox2code.faflaunchmod.ui;

import javafx.beans.binding.BooleanExpression;

public interface FAFLaunchCompatController<ROOT> extends
        FAFLaunchCompatNodeControllerHelper.FAFLaunchCompatController<ROOT> {

    ROOT getRoot();

    BooleanExpression createAttachedExpression();

    BooleanExpression createVisibleExpression();
}
