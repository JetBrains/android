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

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.intellij.openapi.util.Ref;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class EspressoRecorderTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

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
   * TT ID: 908a36f6-4e89-4031-8b3d-c2a75ddc7f08
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
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/72160209
  @Test
  public void addDependencyOnFly() throws Exception {
    guiTest.importSimpleLocalApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture
      .invokeMenuPath("Run", "Record Espresso Test");
    DeployTargetPickerDialogFixture.find(guiTest.robot())
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture.getDebugToolWindow()
      .findContent(TEST_RECORDER_APP)
      .waitForOutput(new PatternTextMatcher(DEBUG_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);

    RecordingDialogFixture.find(guiTest.robot()).clickOk();
    TestClassNameInputDialogFixture.find(guiTest.robot()).clickOk();
    MessagesFixture.findByTitle(guiTest.robot(), "Missing Espresso dependencies").clickYes();

    // Run Android test.
    ideFrameFixture.waitForGradleProjectSyncToFinish();
    // The test menu item does not appear in the menu until we interact with the editor
    // This is a minor bug: https://issuetracker.google.com/71516507
    // Generate a click in the editor:
    try {
      ideFrameFixture.getEditor()
        .waitUntilErrorAnalysisFinishes()
        .moveBetween("public class ", "MyActivityTest");
    } catch(WaitTimedOutError ignored) {
      // We do not care if our cursor is not where it needs to be. We
      // just needed a click inside the editor.
    }

    Ref<JListFixture> popupList = new Ref<>();
    Wait.seconds(20).expecting("The instrumentation test is ready").until(() -> {
      ideFrameFixture.invokeMenuPath("Run", "Run...");
      JListFixture listFixture = new JListFixture(guiTest.robot(), GuiTests.waitForPopup(guiTest.robot()));
      if (Arrays.asList(listFixture.contents()).contains("Wrapper[MyActivityTest]")) {
        popupList.set(listFixture);
        return true;
      } else {
        guiTest.robot().pressAndReleaseKeys(KeyEvent.VK_ESCAPE);
        return false;
      }
    });

    popupList.get().clickItem("Wrapper[MyActivityTest]");

    // Stop the emulator so the AVD name appears consistently in the deployment dialog:
    emulator.getEmulatorConnection().killEmulator();
    DeployTargetPickerDialogFixture.find(guiTest.robot())
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    // Wait until tests run completion.
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(RUN_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);
  }
}
