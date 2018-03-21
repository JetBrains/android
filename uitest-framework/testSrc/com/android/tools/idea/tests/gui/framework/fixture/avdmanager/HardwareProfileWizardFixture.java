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

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class HardwareProfileWizardFixture extends AbstractWizardFixture<HardwareProfileWizardFixture> {

  public static HardwareProfileWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Hardware Profile Configuration"));
    return new HardwareProfileWizardFixture(robot, dialog);
  }

  public HardwareProfileWizardFixture(Robot robot, JDialog target) {
    super(HardwareProfileWizardFixture.class, robot, target);
  }

  @NotNull
  public ConfigureDeviceOptionsStepFixture<HardwareProfileWizardFixture> getConfigureDeviceOptionsStep() {
    JRootPane rootPane = findStepWithTitle("Configure Hardware Profile");
    return new ConfigureDeviceOptionsStepFixture<>(this, rootPane);
  }

  @NotNull
  public HardwareProfileWizardFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }

  @NotNull
  public HardwareProfileWizardFixture clickFinish() {
    super.clickFinish(Wait.seconds(10));
    return this;
  }
}
