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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdEditWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseDeviceDefinitionStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ConfigureAvdOptionsStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.DeviceManagerToolWindowFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class DeviceConfigurationDialogTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  private List<String> expectedCategories = List.of("Phone", "Tablet", "Wear OS", "Desktop", "TV", "Automotive");
  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Activity";

  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE); // Default projects are created with androidx dependencies
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    /*
    # User need to install emulator first
    IdeSettingsDialogFixture ideSettingsDialogFixture = guiTest.ideFrame().invokeSdkManager();
    guiTest.robot().waitForIdle();
    findAndClickLabel(ideSettingsDialogFixture, "SDK Tools");
    */

  }

  /*
  This test case need to be updated after Flamingo feature "Device Manager: Improved Device Configuration" is released
  for all default device configurations to be displayed.

  Current status:
  1. Go to device Manager
  2. Click on create device button
  3. Go to Virtual Device Configuration dialog (Verify 1)
  4. Select a device
  5. Click Next
  6. Go to 'System Image' dialog // Since system image and emulator is not downloaded, device cannot be created
  7. Click 'Cancel'

  Verification:
  1. Verify correct category are displayed
   */

  @Test
  public void testDeviceConfiguration() {
    DeviceManagerToolWindowFixture deviceManagerToolWindow = guiTest.ideFrame().invokeDeviceManager();

    AvdEditWizardFixture avdEditWizard = deviceManagerToolWindow.clickCreateDeviceButton();

    avdEditWizard.selectHardware();
    ChooseDeviceDefinitionStepFixture<AvdEditWizardFixture> selectHardware = avdEditWizard.selectHardware();

    assertThat(selectHardware.categoryNames()).isEqualTo(expectedCategories); //Verify Category from left table

    avdEditWizard.selectHardware().enterSearchTerm("Pixel").selectHardwareProfile("Pixel 4");
    avdEditWizard.clickNext();

    ChooseSystemImageStepFixture chooseSystemImageStep = avdEditWizard.getChooseSystemImageStep();
    chooseSystemImageStep.selectTab("Recommended");
    /*
    chooseSystemImageStep.selectSystemImage(new ChooseSystemImageStepFixture.SystemImage("Sv2", "32", "x86_64", "Android 12L (Google Play)"));


    // Since system image and emulator is not downloaded, device cannot be created
    avdEditWizard.clickNext();

    ConfigureAvdOptionsStepFixture configureAvdOptionsStep = avdEditWizard.getConfigureAvdOptionsStep();
    configureAvdOptionsStep.showAdvancedSettings();
    configureAvdOptionsStep.requireAvdName("Pixel XL API 32"); // check default
    configureAvdOptionsStep.setAvdName("Testsuite AVD");
    //configureAvdOptionsStep.setFrontCamera("Emulated");
    avdEditWizard.clickFinish();
    guiTest.waitForBackgroundTasks();

    deviceManagerToolWindow.close();
    */
    avdEditWizard.clickCancel();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

  }

}
