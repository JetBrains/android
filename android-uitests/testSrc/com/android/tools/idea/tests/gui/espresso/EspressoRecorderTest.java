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
package com.android.tools.idea.tests.gui.espresso;

import com.android.tools.idea.tests.gui.emulator.TestWithEmulator;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class EspressoRecorderTest extends TestWithEmulator {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String APP_NAME = "MyActivityTest";
  private static final String TEST_RECORDER_APP = "TestRecorderapp";
  private static final Pattern DEBUG_OUTPUT =
    Pattern.compile(".*google\\.simpleapplication\\.test.*Connecting to google\\.simpleapplication.*", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*adb shell am instrument.*google\\.simpleapplication\\.MyActivityTest.*Tests ran to completion.*", Pattern.DOTALL);

  /**
   * To verify espresso adds dependencies after recording in new project
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581575
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. Run | Record Espresso Test
   *   3. Wait for recording dialog and click OK
   *   5. Wait for test class name input dialog and click OK
   *   6. Click yes to add missing Espresso dependencies
   *   7. Run test
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void addDependencyOnFly() throws Exception {
    guiTest.importSimpleApplication();
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture
      .invokeMenuPath("Run", "Record Espresso Test");
    DeployTargetPickerDialogFixture.find(guiTest.robot())
      .selectDevice(AVD_NAME)
      .clickOk();

    ideFrameFixture.getDebugToolWindow().findContent(TEST_RECORDER_APP).waitForOutput(new PatternTextMatcher(DEBUG_OUTPUT), 120);

    RecordingDialogFixture.find(guiTest.robot()).clickOk();
    TestClassNameInputDialogFixture.find(guiTest.robot()).clickOk();
    MessagesFixture.findByTitle(guiTest.robot(), "Missing Espresso dependencies").clickYes();

    // Run Android test.
    Wait.seconds(5).expecting("The instrumentation test is ready").until(() -> {
      try {
        ideFrameFixture.waitForGradleProjectSyncToFinish()
          .invokeMenuPath("Run", "Run...");
        new JListFixture(guiTest.robot(), GuiTests.waitForPopup(guiTest.robot())).clickItem("Wrapper[MyActivityTest]");
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });

    DeployTargetPickerDialogFixture.find(guiTest.robot())
      .selectDevice(AVD_NAME)
      .clickOk();

    // Wait until tests run completion.
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
  }
}
