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

public class AvdListDialogTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testCreateAvd() throws Exception {
    myProjectFrame = importSimpleApplication();
    AvdManagerDialogFixture avdManagerDialog = myProjectFrame.invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    ChooseDeviceDefinitionStepFixture chooseDeviceDefinitionStep = avdEditWizard.getChooseDeviceDefinitionStep();
    chooseDeviceDefinitionStep.enterSearchTerm("Nexus").selectDeviceByName("Nexus 7");
    avdEditWizard.clickNext();

    ChooseSystemImageStepFixture chooseSystemImageStep = avdEditWizard.getChooseSystemImageStep();
    chooseSystemImageStep.selectSystemImage("KitKat", "19", "x86", "Android 4.4.2");
    avdEditWizard.clickNext();

    ConfigureAvdOptionsStepFixture configureAvdOptionsStep = avdEditWizard.getConfigureAvdOptionsStep();
    configureAvdOptionsStep.showAdvancedSettings();
    configureAvdOptionsStep.requireAvdName("Nexus 7 API 19"); // check default
    configureAvdOptionsStep.setAvdName("Testsuite AVD");
    configureAvdOptionsStep.setFrontCamera("Emulated");
    configureAvdOptionsStep.setScaleFactor("1dp on device = 1px on screen").selectUseHostGpu(true);
    avdEditWizard.clickFinish();
    myProjectFrame.waitForBackgroundTasksToFinish();

    // Ensure the AVD was created
    avdManagerDialog.selectAvdByName("Testsuite AVD");
    // Then clean it up
    avdManagerDialog.deleteAvdByName("Testsuite AVD");

    avdManagerDialog.close();
  }

  @Test @IdeGuiTest
  public void testEditAvd() throws Exception {
    myProjectFrame = importSimpleApplication();

    makeNexus5();

    AvdManagerDialogFixture avdManagerDialog = myProjectFrame.invokeAvdManager();
    AvdEditWizardFixture avdEditWizardFixture = avdManagerDialog.editAvdWithName("Nexus 5 API 19");
    ConfigureAvdOptionsStepFixture configureAvdOptionsStep = avdEditWizardFixture.getConfigureAvdOptionsStep();

    configureAvdOptionsStep.showAdvancedSettings();
    configureAvdOptionsStep.selectUseHostGpu(true);
    
    avdEditWizardFixture.clickFinish();
    avdManagerDialog.close();

    removeNexus5();
  }

  private void makeNexus5() throws Exception {
    AvdManagerDialogFixture avdManagerDialog = myProjectFrame.invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    ChooseDeviceDefinitionStepFixture chooseDeviceDefinitionStep = avdEditWizard.getChooseDeviceDefinitionStep();
    chooseDeviceDefinitionStep.selectDeviceByName("Nexus 5");
    avdEditWizard.clickNext();

    ChooseSystemImageStepFixture chooseSystemImageStep = avdEditWizard.getChooseSystemImageStep();
    chooseSystemImageStep.selectSystemImage("KitKat", "19", "x86", "Android 4.4.2");
    avdEditWizard.clickNext();
    avdEditWizard.clickFinish();
    avdManagerDialog.close();
  }

  private void removeNexus5() {
    AvdManagerDialogFixture avdManagerDialog = myProjectFrame.invokeAvdManager();
    avdManagerDialog.deleteAvdByName("Nexus 5 API 19");
  }
}