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
package com.android.tools.idea.tests.gui.emulator;

import static com.android.tools.idea.gradle.util.BuildMode.REBUILD;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.BrowseSamplesWizardFixture;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class LaunchAndroidApplicationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private final EmulatorTestRule emulator = new EmulatorTestRule(false);
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  /**
   * To verify that sample ndk projects can be imported and breakpoints in code are hit.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 1eb26e7c-5127-49aa-83c9-32d9ff160315
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import Teapot from the cloned repo
   *   3. Open TeapotNativeActivity.cpp
   *   4. Click on any method and make sure it finds the method or its declaration
   *   5. Search for the symbol HandleInput and put a break point in the method
   *   6. Deploy to a device/emulator
   *   7. Wait for notification that the debugger is connected
   *   8. Teapot should show up on the device
   *   9. Touch to interact
   *   Verify:
   *   1. After step 9, the breakpoint is triggered.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/70732009
  @Test
  public void testCppDebugOnEmulatorWithBreakpoint() throws Exception {
    BrowseSamplesWizardFixture samplesWizard = guiTest.welcomeFrame()
      .importCodeSample();
    samplesWizard.selectSample("Ndk/Teapots")
      .clickNext()
      .getConfigureFormFactorStep()
      .enterApplicationName("TeapotTest");

    IdeFrameFixture ideFrameFixture = IdeFrameFixture.actAndWaitForGradleProjectSyncToFinish(() -> {
      samplesWizard.clickFinish();
      return guiTest.ideFrame();
    });

    ideFrameFixture
      .getEditor()
      .open("classic-teapot/src/main/cpp/TeapotNativeActivity.cpp")
      .moveBetween("g_engine.Draw", "Frame()")
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT) // First break point - First Frame is drawn
      .moveBetween("static int32_t Handle", "Input(")
      .invokeAction(EditorFixture.EditorAction.GOTO_IMPLEMENTATION)
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT); // Second break point - HandleInput()

    assertThat(guiTest.ideFrame().getEditor().getCurrentLine())
      .contains("int32_t Engine::HandleInput(");

    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture.debugApp("classic-teapot", emulator.getDefaultAvdName());

    Wait.seconds(EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS)
      .expecting("emulator with the app launched in debug mode")
      .until(() -> ideFrameFixture.getDebugToolWindow().getContentCount() >= 2);

    // Wait for the UI App to be up and running, by waiting for the first Frame draw to get hit.
    expectBreakPoint("g_engine.DrawFrame()");

    // Simulate a screen touch
    emulator.getEmulatorConnection().tapRunningAvd(400, 800);

    // Wait for the Cpp HandleInput() break point to get hit.
    expectBreakPoint("Engine* eng = (Engine*)app->userData;");
  }

  private void expectBreakPoint(String lineText) {
    DebugToolWindowFixture debugToolWindow = guiTest.ideFrame()
      .getDebugToolWindow()
      .waitForBreakPointHit();

    // Check we have the right debug line
    assertThat(guiTest.ideFrame().getEditor().getCurrentLine())
      .contains(lineText);

    // Remove break point
    guiTest.ideFrame()
      .getEditor()
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT);

    debugToolWindow.pressResumeProgram();
  }

  /**
   * To verify that build cache can be turned off successfully and ensure that android.dexOptions.preDexLibraries is either true or false
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 73c60c6c-6228-41ad-87b1-2f0708bbb50e
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. <user-home-directory>/.android/build-cache delete the existing build-cache directory.
   *   3. Open the gradle.properties and don't do any modification
   *   4. Build the project (verify 1)
   *   5. Repeat 1 to 4 with below modifications
   *   On (3),add android.enableBuildCache=false (verify 2)
   *   On (3),add android.enableBuildCache=true (verify 3)
   *   Verify:
   *   1. You should be able to see build-cache generated with cached data in <user-home-directory>/.android/build-cache . Build cache will be enalbed by default
   *   2. You should see that build-cache is not generated.
   *   3. Verify that build-cache is generated.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/114304149, fast
  @Test
  public void turnOnOrOffBuildCache() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();

    File homeDir = new File(SystemProperties.getUserHome());
    File androidHomeDir = new File(homeDir, ".android");
    File buildCacheDir = new File(androidHomeDir, "build-cache");
    FileUtil.delete(buildCacheDir);

    ideFrameFixture.invokeAndWaitForBuildAction("Build", "Rebuild Project");
    assertThat(buildCacheDir.exists()).isTrue();

    FileUtil.delete(buildCacheDir);
    ideFrameFixture.getEditor()
      .open("gradle.properties")
      .moveBetween("true", "")
      .enterText("\nandroid.enableBuildCache=false");
    ideFrameFixture.invokeAndWaitForBuildAction("Build", "Rebuild Project");
    assertThat(buildCacheDir.exists()).isFalse();

    ideFrameFixture.getEditor()
      .open("gradle.properties")
      .select("(false)")
      .enterText("true");
    ideFrameFixture.invokeAndWaitForBuildAction("Build", "Rebuild Project");
    assertThat(buildCacheDir.exists()).isTrue();
  }
}
