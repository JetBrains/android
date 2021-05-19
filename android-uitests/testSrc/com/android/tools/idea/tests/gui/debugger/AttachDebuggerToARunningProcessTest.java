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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(GuiTestRemoteRunner.class)
public class AttachDebuggerToARunningProcessTest extends DebuggerTestBase {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES).settingNdkPath();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String DUAL = "Dual";
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final String C_FILE_NAME = "app/src/main/jni/native-lib.c";
  private static final String JAVA_FILE_NAME =
    "app/src/main/java/com/example/basiccmakeapp/MainActivity.java";

  /**
   * Verifies that native debugger is attached to a running process.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: a712ecd1-2c60-4015-ab81-921435d5fb2a
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import AttachDebuggerToProcess and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Run app on the emulator.
   *   4. Set breakpoint in Java and C++ code.
   *   5. Attach "Dual" debugger to running process.
   *   6. OK.
   *   Verify:
   *   1. Verify that dual debugger running, Java and C++ breakpoints are hit.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/114304149, fast
  @Test
  public void testWithDualDebugger() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/AttachDebuggerToProcess");
    emulator.createDefaultAVD(ideFrame.invokeAvdManager());

    ideFrame.runApp(DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());

    // This step is to make sure the process is running.
    ideFrame.getRunToolWindow().findContent("app")
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    // Setup C++ and Java breakpoints.
    openAndToggleBreakPoints(ideFrame,
                             C_FILE_NAME,
                             "return (*env)->NewStringUTF(env, message);");
    openAndToggleBreakPoints(ideFrame,
                             JAVA_FILE_NAME,
                             "s = stringFromJNI();");

    ideFrame.findAttachDebuggerToAndroidProcessButton().click();
    ChooseProcessDialogFixture chooseProcessDialog = ChooseProcessDialogFixture.find(ideFrame);
    chooseProcessDialog.selectDebuggerType(DUAL);
    chooseProcessDialog.clickOk();

    // Check break point is hit by checking related source file is opened.
    Wait.seconds(30)
      .expecting("The Java file is expecting opened because of break point is hit.")
      .until(() -> {
        try {
          ideFrame.getEditor().getCurrentFileName().equals(JAVA_FILE_NAME);
          return true;
        } catch (NullPointerException e) {
          return false;
        }
      });

    ideFrame.invokeMenuPath("Run", "Resume Program");

    Wait.seconds(30)
      .expecting("The C file is expecting opened because of break point is hit.")
      .until(() -> {
        try {
          ideFrame.getEditor().getCurrentFileName().equals(C_FILE_NAME);
          return true;
        }  catch (NullPointerException e) {
          return false;
        }
      });

    // TODO: Add more verifications for hitting break point, e.g. the line is high lighted,
    // verify variable values in Debug Tool Window, whose fixture needs to be improved.
  }

}
