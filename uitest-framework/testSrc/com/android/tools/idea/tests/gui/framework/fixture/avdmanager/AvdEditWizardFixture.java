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
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class AvdEditWizardFixture extends AbstractWizardFixture<AvdEditWizardFixture> {
  public static AvdEditWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, null, Matchers.byTitle(JDialog.class, "Virtual Device Configuration"), 30);
    return new AvdEditWizardFixture(robot, dialog);
  }

  public AvdEditWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(AvdEditWizardFixture.class, robot, target);
  }

  @NotNull
  public ChooseDeviceDefinitionStepFixture<AvdEditWizardFixture> selectHardware() {
    JRootPane rootPane = findStepWithTitle("Select Hardware");
    return new ChooseDeviceDefinitionStepFixture<>(this, rootPane);
  }

  @NotNull
  public ChooseSystemImageStepFixture<AvdEditWizardFixture> getChooseSystemImageStep() {
    // Can't search for "System Image" because that appears as a label in the system image
    // description panel on the right
    JRootPane rootPane = findStepWithTitle("Select a system image");
    return new ChooseSystemImageStepFixture<>(this, rootPane);
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<AvdEditWizardFixture> getConfigureAvdOptionsStep() {
    JRootPane rootPane = findStepWithTitle("Verify Configuration");
    return new ConfigureAvdOptionsStepFixture<>(this, rootPane);
  }

  @NotNull
  public AvdEditWizardFixture clickFinish() {
    super.clickFinish(Wait.seconds(30));
    return this;
  }

  @NotNull
  public AvdEditWizardFixture clickCancel() {
    super.clickCancel();
    return this;
  }
}
