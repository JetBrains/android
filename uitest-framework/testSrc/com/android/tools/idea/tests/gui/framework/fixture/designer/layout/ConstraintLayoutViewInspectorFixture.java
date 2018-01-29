/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.JComponentFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.handlers.constraint.MarginWidget;
import com.android.tools.idea.uibuilder.handlers.constraint.SingleWidgetView;
import com.android.tools.idea.uibuilder.handlers.constraint.SingleWidgetView.KillButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class ConstraintLayoutViewInspectorFixture {
  private final Robot myRobot;
  private final Container myTarget;

  public ConstraintLayoutViewInspectorFixture(@NotNull Robot robot, @NotNull Container target) {
    myRobot = robot;
    myTarget = target;
  }

  public void selectMarginStart(int margin) {
    myRobot.moveMouse(myRobot.finder().findByName(myTarget, SingleWidgetView.TOP_MARGIN_WIDGET));

    GenericTypeMatcher<JComboBox> matcher = Matchers.byName(JComboBox.class, SingleWidgetView.TOP_MARGIN_WIDGET + "ComboBox");
    new JComboBoxFixture(myRobot, GuiTests.waitUntilShowing(myRobot, myTarget, matcher)).selectItem(Integer.toString(margin));
  }

  public void setAllMargins(int n) {
    Component comp = myRobot.finder().findByName(SingleWidgetView.TOP_MARGIN_WIDGET);
    if (comp != null) {
      ((MarginWidget)comp).setMargin(n);
    }

    comp = myRobot.finder().findByName(SingleWidgetView.BOTTOM_MARGIN_WIDGET);
    if (comp != null) {
      ((MarginWidget)comp).setMargin(n);
    }

    comp = myRobot.finder().findByName(SingleWidgetView.RIGHT_MARGIN_WIDGET);
    if (comp != null) {
      ((MarginWidget)comp).setMargin(n);
    }

    comp = myRobot.finder().findByName(SingleWidgetView.LEFT_MARGIN_WIDGET);
    if (comp != null) {
      ((MarginWidget)comp).setMargin(n);
    }
  }

  public void scrollAllMargins(int scroll) {
    Component comp = myRobot.finder().findByName(SingleWidgetView.TOP_MARGIN_WIDGET);
    myRobot.rotateMouseWheel(comp, scroll);
    comp = myRobot.finder().findByName(SingleWidgetView.BOTTOM_MARGIN_WIDGET);
    myRobot.rotateMouseWheel(comp, scroll);
    comp = myRobot.finder().findByName(SingleWidgetView.RIGHT_MARGIN_WIDGET);
    myRobot.rotateMouseWheel(comp, scroll);
    comp = myRobot.finder().findByName(SingleWidgetView.LEFT_MARGIN_WIDGET);
    myRobot.rotateMouseWheel(comp, scroll);
  }

  @NotNull
  public KillButtonFixture getDeleteRightConstraintButton() {
    return new KillButtonFixture(myRobot, myTarget, "deleteRightConstraintButton");
  }

  public static final class KillButtonFixture extends JComponentFixture<KillButtonFixture, KillButton> {
    private KillButtonFixture(@NotNull Robot robot, @NotNull Container ancestorTarget, @NotNull String name) {
      super(KillButtonFixture.class, robot, (KillButton)robot.finder().findByName(ancestorTarget, name));
    }
  }
}