/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class AvdEditWizardFixture extends AbstractWizardFixture {

  public static AvdEditWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        return "Virtual Device Configuration".equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
    return new AvdEditWizardFixture(robot, dialog);
  }

  public AvdEditWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(robot, target);
  }

  public ChooseDeviceDefinitionStepFixture getChooseDeviceDefinitionStep() {
    JRootPane rootPane = findStepWithTitle("Select Hardware");
    return new ChooseDeviceDefinitionStepFixture(robot, rootPane);
  }

  public ChooseSystemImageStepFixture getChooseSystemImageStep() {
    JRootPane rootPane = findStepWithTitle("System Image");
    return new ChooseSystemImageStepFixture(robot, rootPane);
  }

  public ConfigureAvdOptionsStepFixture getConfigureAvdOptionsStep() {
    JRootPane rootPane = findStepWithTitle("Configure AVD");
    return new ConfigureAvdOptionsStepFixture(robot, rootPane);
  }

  @NotNull
  public AvdEditWizardFixture clickNext() {
    JButton button = findButtonByText("Next");
    robot.click(button);
    return this;
  }

  @NotNull
  public AvdEditWizardFixture clickFinish() {
    JButton button = findButtonByText("Finish");
    robot.click(button);
    return this;
  }

  @NotNull
  public AvdEditWizardFixture clickCancel() {
    JButton button = findButtonByText("Cancel");
    robot.click(button);
    return this;
  }
}
