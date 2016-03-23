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


public class AvdEditWizardFixture extends AbstractWizardFixture<AvdEditWizardFixture> {
  public static AvdEditWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return "Virtual Device Configuration".equals(dialog.getTitle());
      }
    });
    return new AvdEditWizardFixture(robot, dialog);
  }

  public AvdEditWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(AvdEditWizardFixture.class, robot, target);
  }

  @NotNull
  public ChooseDeviceDefinitionStepFixture getChooseDeviceDefinitionStep() {
    JRootPane rootPane = findStepWithTitle("Select Hardware");
    return new ChooseDeviceDefinitionStepFixture(robot(), rootPane);
  }

  @NotNull
  public ChooseSystemImageStepFixture getChooseSystemImageStep() {
    // Can't search for "System Image" because that appears as a label in the system image
    // description panel on the right
    JRootPane rootPane = findStepWithTitle("Select a system image");
    return new ChooseSystemImageStepFixture(robot(), rootPane);
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture getConfigureAvdOptionsStep() {
    JRootPane rootPane = findStepWithTitle("Verify Configuration");
    return new ConfigureAvdOptionsStepFixture(robot(), rootPane);
  }
}
