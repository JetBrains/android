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

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.*;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * Tests exercising the UI for hardware profile management
 */
public class HardwareProfileTest extends GuiTestCase {
  @Test
  @IdeGuiTest
  public void testCreateHardwareProfile() throws Exception {
    myProjectFrame = importSimpleApplication();
    AvdManagerDialogFixture avdManagerDialog = myProjectFrame.invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();
    ChooseDeviceDefinitionStepFixture chooseDeviceDefinitionStep = avdEditWizard.getChooseDeviceDefinitionStep();

    // UI tests are not as isolated as we would like, make sure there's no name clash.
    final String deviceName = "device-" + System.currentTimeMillis();
    assertFalse("Device with this name already exists, no point in testing.", chooseDeviceDefinitionStep.deviceExists(deviceName));

    DeviceEditWizardFixture deviceEditWizard = chooseDeviceDefinitionStep.createNewDevice();
    ConfigureDeviceOptionsStepFixture deviceOptionsStep = deviceEditWizard.getConfigureDeviceOptionsStep();
    deviceOptionsStep.setDeviceName(deviceName)
                     .selectHasFrontCamera(false)
                     .setScreenResolutionX(1280)
                     .setScreenResolutionY(920)
                     .setScreenSize(5.2);
    myRobot.waitForIdle();
    deviceEditWizard.clickOk();
    chooseDeviceDefinitionStep.selectDeviceByName(deviceName);
    chooseDeviceDefinitionStep.removeDeviceByName(deviceName);
    avdEditWizard.clickCancel();
    avdManagerDialog.close();
  }
}
