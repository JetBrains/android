/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.avdmanager;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.project.Project;
import java.awt.Component;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

public class DeviceManagerToolWindowFixture extends ToolWindowFixture {

  @NotNull
  public DeviceManagerToolWindowFixture(@NotNull Project project, @NotNull Robot robot) {
    super("Device Manager 2", project, robot);
  }

  public AvdEditWizardFixture clickCreateDeviceButton() {
    ActionButton addDeviceButton = waitUntilShowingAndEnabled(robot(), null, new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override protected boolean isMatching(@NotNull ActionButton actionButton) {
        return "Add Device".equals(actionButton.getAccessibleContext().getAccessibleName());
      }
    });
    robot().click(addDeviceButton);

    Component createVirtualDevice = GuiTests.waitUntilShowingAndEnabled(
      myRobot,
      null,
      Matchers.byText(ActionMenuItem.class, "Create Virtual Device"));
    myRobot.click(createVirtualDevice);

    return AvdEditWizardFixture.find(robot());
  }
}