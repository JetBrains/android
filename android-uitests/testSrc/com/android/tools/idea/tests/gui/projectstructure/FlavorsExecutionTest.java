/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.projectstructure;

import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureBasicActivityStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class FlavorsExecutionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String PROCESS_NAME = "google.simpleapplication";
  private static final String ACTIVITY_OUTPUT_PATTERN =
    ".*adb shell am start .*google\\.simpleapplication\\.Main_Activity.*Connected to process.*";
  private static final String FIRST_ACTIVITY_NAME = "F1_Main_Activity";
  private static final String SECOND_ACTIVITY_NAME = "F2_Main_Activity";

  @Before
  public void setUp() throws Exception {
    guiTest.importSimpleApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
  }

  /***
   * To verify that the selected app flavor activity can be launched using build variants
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 5bf8bdbc-2ef1-4cd7-aa13-cbc91323cac9
   * <pre>
   *   Test Steps:
   *   1. Create a new project
   *   2. Open Project Structure Dialog
   *   3. Select app module, add two new flavors (Flavor1 and Flavor2)
   *   4. Switch to Project View
   *   5. Select app
   *   6. Add launcher activities under Flavor1 and Flavor2 and name them F1_Main_Activity and F2_Main_Activity
   *   7. Open Build variants window and select flavor1Debug
   *   8. Deploy the project on an AVD (Verify 1)
   *   9. Select flavor2Debug from Build variants
   *   10. Deploy the project on an AVD (Verify 2)
   *   Verification:
   *   1. Verify in Android Run tool window for the launch of F1_Main_Activity
   *   2. Verify in Android Run tool window for the launch of F2_Main_Activity
   * </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37150124
  @Test
  public void runBuildFlavors() throws Exception {
    InstantRunSettings.setShowStatusNotifications(false);
    guiTest.ideFrame()
      .openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("app")
      .selectFlavorsTab()
      .clickAddButton()
      .setFlavorName("flavor1")
      .clickAddButton()
      .setFlavorName("flavor2")
      .clickOk();
    guiTest.ideFrame()
      .getProjectView()
      .selectProjectPane()
      .clickPath("SimpleApplication", "app")
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Basic Activity")
      .getConfigureActivityStep()
      .enterTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.NAME, FIRST_ACTIVITY_NAME)
      .enterTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.LAYOUT, "activity_f1_main")
      .selectLauncherActivity()
      .setTargetSourceSet("flavor1")
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
    guiTest.ideFrame()
      .getProjectView()
      .selectProjectPane()
      .clickPath("SimpleApplication", "app")
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Basic Activity")
      .getConfigureActivityStep()
      .enterTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.NAME, SECOND_ACTIVITY_NAME)
      .enterTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.LAYOUT, "activity_f2_main")
      .selectLauncherActivity()
      .setTargetSourceSet("flavor2")
      .clickFinish()
      .waitForGradleProjectSyncToFinish();

    guiTest.ideFrame()
      .getBuildVariantsWindow()
      .selectVariantForModule("app", "flavor1Debug");
    guiTest.ideFrame()
      .runApp("app")
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();
    guiTest.ideFrame().getRunToolWindow().findContent("app")
      .waitForOutput(new PatternTextMatcher(Pattern.compile(
        ACTIVITY_OUTPUT_PATTERN.replace("Main_Activity", FIRST_ACTIVITY_NAME), Pattern.DOTALL)), 120);
    guiTest.ideFrame()
      .getAndroidToolWindow()
      .selectDevicesTab()
      .selectProcess(PROCESS_NAME);

    guiTest.ideFrame()
      .getBuildVariantsWindow()
      .selectVariantForModule("app", "flavor2Debug");
    guiTest.ideFrame()
      .runApp("app")
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();
    guiTest.ideFrame().getRunToolWindow().findContent("app")
      .waitForOutput(new PatternTextMatcher(Pattern.compile(
        ACTIVITY_OUTPUT_PATTERN.replace("Main_Activity", SECOND_ACTIVITY_NAME), Pattern.DOTALL)), 120);
    guiTest.ideFrame()
      .getAndroidToolWindow()
      .selectDevicesTab()
      .selectProcess(PROCESS_NAME);
  }
}
