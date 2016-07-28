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
package com.android.tools.idea.tests.gui.avdmanager;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * Tests exercising the UI for hardware profile management
 */
@RunWith(GuiTestRunner.class)
public class HardwareProfileTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void createAndDeleteHardwareProfile() throws Exception {
    String deviceName = HardwareProfileTest.class.getSimpleName();

    guiTest.importSimpleApplication();
    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();
    ChooseDeviceDefinitionStepFixture step = avdEditWizard.selectHardware();
    if (step.deviceNames().contains(deviceName)) {
      step.deleteHardwareProfile(deviceName);
    }

    assertWithMessage("initial state").that(step.deviceNames()).doesNotContain(deviceName);

    HardwareProfileWizardFixture hardwareProfileWizard = step.newHardwareProfile();
    ConfigureDeviceOptionsStepFixture deviceOptionsStep = hardwareProfileWizard.getConfigureDeviceOptionsStep();
    deviceOptionsStep.setDeviceName(deviceName);
    guiTest.robot().waitForIdle();
    hardwareProfileWizard.clickFinish();
    assertWithMessage("after creating").that(step.deviceNames()).contains(deviceName);

    step.deleteHardwareProfile(deviceName);
    assertWithMessage("after deleting").that(step.deviceNames()).doesNotContain(deviceName);

    avdEditWizard.clickCancel();
    avdManagerDialog.close();
  }

  @Test
  public void testCreateHardwareProfileErrors() throws Exception {
    guiTest.importSimpleApplication();
    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();
    ChooseDeviceDefinitionStepFixture chooseDeviceDefinitionStep = avdEditWizard.selectHardware();

    HardwareProfileWizardFixture hardwareProfileWizard = chooseDeviceDefinitionStep.newHardwareProfile();
    ConfigureDeviceOptionsStepFixture deviceOptionsStep = hardwareProfileWizard.getConfigureDeviceOptionsStep();

    deviceOptionsStep.setDeviceName("\b");
    assertThat(deviceOptionsStep.getValidationText())
      .isEqualTo("Please write a name for the new device.");

    deviceOptionsStep.setDeviceName("My Device Test")
      .setScreenSize("5.2x");
    assertThat(deviceOptionsStep.getValidationText())
      .isEqualTo("Please enter a non-zero positive floating point value for the screen size.");

    deviceOptionsStep.setScreenSize("5.2")
      .setScreenResolutionX("0");
    assertThat(deviceOptionsStep.getValidationText())
      .isEqualTo("Please enter a valid value for the screen width.");

    deviceOptionsStep.setScreenResolutionX("1280")
      .setScreenResolutionY("0");
    assertThat(deviceOptionsStep.getValidationText())
      .isEqualTo("Please enter a valid value for the screen height.");

    hardwareProfileWizard.clickCancel();
    avdEditWizard.clickCancel();
    avdManagerDialog.close();
  }
}
