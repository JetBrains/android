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
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdEditWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ConfigureAvdOptionsStepFixture;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRunner.class)
public class AvdListDialogTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Ignore("http://wpie20.hot.corp.google.com:8200/builders/ubuntu-studio-master-dev-uitests/builds/28/")
  @Test
  public void testCreateAvd() throws Exception {
    guiTest.importSimpleApplication();
    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    avdEditWizard.selectHardware().enterSearchTerm("Nexus").selectHardwareProfile("Nexus 7");
    avdEditWizard.clickNext();

    ChooseSystemImageStepFixture chooseSystemImageStep = avdEditWizard.getChooseSystemImageStep();
    chooseSystemImageStep.selectTab("x86 Images");
    chooseSystemImageStep.selectSystemImage("KitKat", "19", "x86", "Android 4.4");
    avdEditWizard.clickNext();

    ConfigureAvdOptionsStepFixture configureAvdOptionsStep = avdEditWizard.getConfigureAvdOptionsStep();
    configureAvdOptionsStep.showAdvancedSettings();
    configureAvdOptionsStep.requireAvdName("Nexus 7 API 19"); // check default
    configureAvdOptionsStep.setAvdName("Testsuite AVD");
    configureAvdOptionsStep.setFrontCamera("Emulated");
    avdEditWizard.clickFinish();
    guiTest.waitForBackgroundTasks();

    // Ensure the AVD was created
    avdManagerDialog.selectAvd("Testsuite AVD");
    // Then clean it up
    avdManagerDialog.deleteAvd("Testsuite AVD");

    avdManagerDialog.close();
  }

  @Ignore("http://wpie20.hot.corp.google.com:8200/builders/ubuntu-studio-master-dev-uitests/builds/28/")
  @Test
  public void testEditAvd() throws Exception {
    guiTest.importSimpleApplication();

    makeNexus5();

    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    AvdEditWizardFixture avdEditWizardFixture = avdManagerDialog.editAvdWithName("Nexus 5 API 19");
    ConfigureAvdOptionsStepFixture configureAvdOptionsStep = avdEditWizardFixture.getConfigureAvdOptionsStep();

    configureAvdOptionsStep.showAdvancedSettings();
    configureAvdOptionsStep.selectGraphicsHardware();

    avdEditWizardFixture.clickFinish();
    avdManagerDialog.close();

    removeNexus5();
  }

  private void makeNexus5() throws Exception {
    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    avdEditWizard.selectHardware().selectHardwareProfile("Nexus 5");
    avdEditWizard.clickNext();

    ChooseSystemImageStepFixture chooseSystemImageStep = avdEditWizard.getChooseSystemImageStep();
    chooseSystemImageStep.selectTab("x86 Images");
    chooseSystemImageStep.selectSystemImage("KitKat", "19", "x86", "Android 4.4");
    avdEditWizard.clickNext();
    avdEditWizard.clickFinish();
    avdManagerDialog.close();
  }

  private void removeNexus5() {
    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    avdManagerDialog.deleteAvd("Nexus 5 API 19");
  }
}
