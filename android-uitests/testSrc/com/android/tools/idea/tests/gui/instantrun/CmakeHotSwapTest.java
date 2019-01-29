/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.instantrun;

import com.android.tools.idea.tests.gui.emulator.DeleteAvdsRule;
import com.android.tools.idea.tests.gui.emulator.EmulatorGenerator;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.android.tools.idea.tests.gui.instantrun.InstantRunTestUtility.extractPidFromOutput;
import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class CmakeHotSwapTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  private final EmulatorTestRule emulator = new EmulatorTestRule(false);
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  private static final String APP_NAME = "app";
  private static final Pattern CMAKE_RUN_OUTPUT =
    Pattern.compile(".*adb shell am start .*google\\.basiccmake.*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final Pattern HOT_SWAP_OUTPUT =
    Pattern.compile(".*Hot swapped changes, activity restarted.*", Pattern.DOTALL);

  /**
   * Verifies that instant run hot swap works as expected on a C++ support project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 23a7eaf2-aba0-4aca-9bd8-28f9c24c855b
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmake.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit a java file.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run hot swap is applied in Run tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/76023451
  @Test
  public void cmakeHotSwap() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("BasicCmake");
    String avdName = EmulatorGenerator.ensureDefaultAvdIsCreated(ideFrameFixture.invokeAvdManager());

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(avdName)
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(CMAKE_RUN_OUTPUT), 120);
    String pid = extractPidFromOutput(contentFixture.getOutput(10), CMAKE_RUN_OUTPUT);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/java/google/basiccmake/MainActivity.java")
      .moveBetween("tv.setText(stringFromJNI());", "")
      .enterText("\nSystem.out.println(\"Hello, CMake hot swap!\");");

    ideFrameFixture
      .findApplyChangesButton()
      .click();

    contentFixture.waitForOutput(new PatternTextMatcher(HOT_SWAP_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput(10), CMAKE_RUN_OUTPUT);
    // (Hot swap) Verify the equality of PIDs before and after IR.
    assertThat(pid).isEqualTo(newPid);
  }
}
