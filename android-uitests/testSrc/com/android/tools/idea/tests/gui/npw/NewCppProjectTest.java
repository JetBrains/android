/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.tests.gui.emulator.TestWithEmulator;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class NewCppProjectTest extends TestWithEmulator {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String APP_NAME = "app";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(".*adb shell am start .*myapplication\\.MainActivity.*", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);


  /**
   * Verify creating a new project from default template.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14603474
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project; check the box "Include C++ Support"
   *   2. Click next until you get to the window for "Customize C++ Support" don't check any boxes
   *   3. Click Finish (Verify 1)
   *   4. Run (Verify 2)
   *
   *   Repeat 1 to 4 with below modifications:
   *   On (2), check Exceptions support, then on (4) verify that  android.defaultConfig.cmake.cppFlags has "-fexceptions"
   *   On (2), check Runtime Type Information Support, then on (4) verify that android.defaultConfig.cmake.cppFlags has "-frtti"
   *   On (2), check both Exceptions & Runtime Type, then verify that android.defaultConfig.cmake.cppFlags has "-fexceptions -frtti"
   *   in any order."
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testCreateNewProjectWithCpp1() throws Exception {
    testCreateNewProjectWithCpp(false, false);
  }

  @RunIn(TestGroup.QA)
  @Test
  public void testCreateNewProjectWithCpp2() throws Exception {
    testCreateNewProjectWithCpp(true, false);
  }

  @RunIn(TestGroup.QA)
  @Test
  public void testCreateNewProjectWithCpp3() throws Exception {
    testCreateNewProjectWithCpp(false, true);
  }

  @RunIn(TestGroup.QA)
  @Test
  public void testCreateNewProjectWithCpp4() throws Exception {
    testCreateNewProjectWithCpp(true, true);
  }

  private void testCreateNewProjectWithCpp(boolean hasExceptionSupport, boolean hasRuntimeInformation) throws Exception {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.getConfigureAndroidProjectStep()
      .setCppSupport(true); // Default "App name", "company domain" and "package name"
    guiTest.setProjectPath(configureAndroidProjectStep.getLocationInFileSystem());
    newProjectWizard.clickNext();
    newProjectWizard.clickNext(); // Skip "Select minimum SDK Api" step
    newProjectWizard.clickNext(); // Skip "Add Activity" step
    newProjectWizard.clickNext(); // Use default activity names

    newProjectWizard.getConfigureCppStepFixture()
      .setExceptionsSupport(hasExceptionSupport)
      .setRuntimeInformationSupport(hasRuntimeInformation);

    newProjectWizard.clickFinish();

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish();

    String gradleCppFlags = guiTest.ideFrame().getEditor()
      .open("app/build.gradle")
      .moveBetween("cppFlags \"", "")
      .getCurrentLine();

    String cppFlags = String.format("%s %s", hasRuntimeInformation ? "-frtti" : "",  hasExceptionSupport ? "-fexceptions" : "").trim();
    Assert.assertEquals(String.format("cppFlags \"%s\"", cppFlags), gradleCppFlags.trim());

    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
  }
}
