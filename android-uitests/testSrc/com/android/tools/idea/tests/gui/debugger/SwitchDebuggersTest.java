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
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseProcessDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith (GuiTestRunner.class)
public class SwitchDebuggersTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String JAVA = "Java";
  private static final String NATIVE = "Native";
  private static final String RUN_CONFIG_NAME = "app";
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final String MAIN_JAVA_FILE = "MainActivity.java";
  private static final String MAIN2_JAVA_FILE = "Main2Activity.java";
  private static final String C_FILE = "native-lib.cpp";
  private static final String MAIN_JAVA_BKPT = "startActivity(intent);";
  private static final String MAIN2_JAVA_BKPT1 = "setSupportActionBar(toolbar);";
  private static final String MAIN2_JAVA_BKPT2 = "tv.setText(stringFromJNI());";
  private static final String C_BKPT = "return env->NewStringUTF(hello.c_str());";
  private static final String JAVA_SRC_PATH =
    "app/src/main/java/com/example/switchdebuggers/";
  private static final String C_SRC_PATH = "app/src/main/cpp/";

  /**
   * Verifies that we can successfully attach the debugger from one to other.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 5c02f60e-444d-4d5a-afc1-15b1998340fe
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SwitchDebuggers.
   *   2. Create an emulator.
   *   3. Set breakpoints both in C and Java codes.
   *   4. Run app on the emulator.
   *   5. Attach Java debugger using “Run->Attach debugger to Android process”.
   *   6. Tap the button in the app, then breakpoints should get hit in Java code.
   *   7. Hit the back button and come back.
   *   8. Now switch to Native debugger, using "Run -> Attach debugger to Android process".
   *   9. Should be able to switch to a new debugger and breakpoints should get hit accordingly.
   *   </pre>
   */
  @Ignore("b/37093995: Attaching the debugger to running process fails while switching one deubugger mode to other")
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/37093995
  public void switchDebuggersUsingAttachToDebugger() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("SwitchDebuggers");

    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    openAndToggleBreakPoints(ideFrame, C_SRC_PATH + C_FILE, C_BKPT);
    openAndToggleBreakPoints(ideFrame, JAVA_SRC_PATH + MAIN_JAVA_FILE, MAIN_JAVA_BKPT);
    openAndToggleBreakPoints(ideFrame,
                             JAVA_SRC_PATH + MAIN2_JAVA_FILE,
                             MAIN2_JAVA_BKPT1,
                             MAIN2_JAVA_BKPT2);

    ideFrame.runApp(RUN_CONFIG_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    // This step is to make sure the process is running.
    ideFrame.getRunToolWindow().findContent(RUN_CONFIG_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    attachDebuggerToAndroidProcess(ideFrame, JAVA, MAIN_JAVA_FILE, MAIN_JAVA_BKPT);

    emulator.getEmulatorConnection().tapBackButtonOnRunningAvd();

    // This test will fail here when switching to a different type of debugger. check b/37093995.
    attachDebuggerToAndroidProcess(ideFrame, NATIVE, C_FILE, C_BKPT);
  }

  private void attachDebuggerToAndroidProcess(@NotNull IdeFrameFixture ideFrame,
                                              @NotNull String debuggerType,
                                              @NotNull String fileName,
                                              @NotNull String breakpointLine) {
    ideFrame.findAttachDebuggerToAndroidProcessButton().click();
    ChooseProcessDialogFixture.find(ideFrame)
      .selectDebuggerType(debuggerType)
      .clickOk();

    Wait.seconds(10).expecting("The button is clicked").until(() -> {
      try {
        // Tap the button in the app to trigger hitting breakpoints.
        // The value for x and y can be get from Device: Developer Option: Pointer Location.
        emulator.getEmulatorConnection().tapRunningAvd(10, 1000);
        return fileName.equals(ideFrame.getEditor().getCurrentFileName());
      } catch (WaitTimedOutError e) {
        return false;
      }
    });
    EditorFixture editor = ideFrame.getEditor();
    assertThat(breakpointLine).isEqualTo(editor.getCurrentLine().trim());
    editor.close();
  }
}
