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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith (GuiTestRunner.class)
public class NewKotlinProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String APP_NAME = "app";
  private static final String KOTLIN_FILE = "MainActivity.kt";
  private static final String C_FILE = "native-lib.cpp";
  private static final Pattern CONNECTED_TO_PROCESS_OUTPUT = Pattern.compile(
    ".*Connected to process.*", Pattern.DOTALL);

  /**
   * Verifies that can creating a new project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 4d4c36b0-23a7-4f16-9293-061e2fb1310f
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a basic Kotlin project following the default steps.
   *   2. Select the "include Kotlin" support checkbox [verify 1 & 2].
   *   3. Build and run project, and verify 3.
   *   Vefify:
   *   1. Check if build is successful.
   *   2. Mainactivity should have .kt as extension and ensure the class has Kotlin code.
   *   3. Ensure the app is deployed on the emulator.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/70339267
  @Test
  public void createBasicKotlinProject() throws Exception {
    createNewBasicKotlinProject(false);

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();
    assertThat(KOTLIN_FILE).isEqualTo(ideFrameFixture.getEditor().getCurrentFileName());

    emulator.createDefaultAVD(ideFrameFixture.invokeAvdManager());
    ideFrameFixture.runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture.getRunToolWindow().findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(CONNECTED_TO_PROCESS_OUTPUT), emulator.DEFAULT_EMULATOR_WAIT_SECONDS);
    ideFrameFixture.stopApp();
  }

  /**
   * Verifies that can creating a new project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 4d4c36b0-23a7-4f16-9293-061e2fb1310f
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a basic Kotlin project following the default steps.
   *   2. Select the "include Kotlin" and "C++" support checkbox [verify 1 & 2].
   *   3. Build and run project on an emulator, and verify step 3.
   *   Vefify:
   *   1. Check if build is successful.
   *   2. C++ code is created, Mainactivity has .kt extension.
   *   3. Ensure the app is deployed on the emulator.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/70339267
  @Test
  public void createCppKotlinProject() throws Exception {
    createNewBasicKotlinProject(true);

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();
    assertThat(KOTLIN_FILE).isEqualTo(ideFrameFixture.getEditor().getCurrentFileName());
    ideFrameFixture.getEditor().open("app/src/main/cpp/" + C_FILE, EditorFixture.Tab.EDITOR);
    assertThat(C_FILE).isEqualTo(ideFrameFixture.getEditor().getCurrentFileName());

    emulator.createDefaultAVD(ideFrameFixture.invokeAvdManager());
    ideFrameFixture.runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture.getRunToolWindow().findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(CONNECTED_TO_PROCESS_OUTPUT), emulator.DEFAULT_EMULATOR_WAIT_SECONDS);
    ideFrameFixture.stopApp();
  }

  private void createNewBasicKotlinProject(boolean hasCppSupport) {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    newProjectWizard.getConfigureAndroidProjectStep()
      .enterPackageName("android.com")
      .setCppSupport(hasCppSupport)
      .setKotlinSupport(true); // Default "App name", "company domain" and "package name"

    newProjectWizard.clickNext();
    newProjectWizard.clickNext(); // Skip "Select minimum SDK Api" step
    newProjectWizard.clickNext(); // Skip "Add Activity" step

    if (hasCppSupport) {
      newProjectWizard.clickNext();
    }

    newProjectWizard.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    // Build project after Gradle sync finished.
    guiTest.ideFrame().invokeMenuPath("Build", "Rebuild Project").waitForBuildToFinish(BuildMode.REBUILD);
  }
}
