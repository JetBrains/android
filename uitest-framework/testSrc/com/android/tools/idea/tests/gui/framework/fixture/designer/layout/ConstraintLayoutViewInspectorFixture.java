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

import com.android.tools.idea.tests.gui.framework.fixture.JComponentFixture;
import com.android.tools.idea.uibuilder.handlers.constraint.MarginWidget;
import com.android.tools.idea.uibuilder.handlers.constraint.SingleWidgetView;
import com.android.tools.idea.uibuilder.handlers.constraint.SingleWidgetView.KillButton;
import java.awt.Container;
import javax.swing.JComboBox;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

public final class ConstraintLayoutViewInspectorFixture {
  private final Robot myRobot;
  private final Container myTarget;

  public ConstraintLayoutViewInspectorFixture(@NotNull Robot robot, @NotNull Container target) {
    myRobot = robot;
    myTarget = target;
  }

  public void selectMarginStart(int margin) {
    MarginWidget marginWidget = findMarginWidget(SingleWidgetView.TOP_MARGIN_WIDGET);
    myRobot.moveMouse(marginWidget);
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(myRobot, myRobot.finder().findByType(marginWidget, JComboBox.class));
    comboBoxFixture.selectItem(Integer.toString(margin));
  }

  private void setMargin(String widgetName, int marginValue) {
    MarginWidget widget = findMarginWidget(widgetName);
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(myRobot, myRobot.finder().findByType(widget, JComboBox.class));
    comboBoxFixture.replaceText(Integer.toString(marginValue));
  }

  @NotNull
  private MarginWidget findMarginWidget(String name) {
    return myRobot.finder().findByName(name, MarginWidget.class);
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