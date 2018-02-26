
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
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class JavaDebuggerTest extends DebuggerTestBase {
  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String DEBUG_CONFIG_NAME = "app";

  /**
   * Verifies that Java Debugger works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: TODO: Wait for manual test case to be added.
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicJniAppForUI.
   *   2. Select Java debugger on Edit Configurations dialog.
   *   3. Set breakpoints both in Java and C++ code.
   *   4. Debug on a device running M or earlier.
   *   5. When the Java breakpoint is hit, verify variables and resume.
   *   6. C++ breakpoint is not hit.
   *   7. Stop debugging.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testJavaDebugger() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicCmakeAppForUI");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    DebuggerTestUtil.setDebuggerType(ideFrameFixture, DebuggerTestUtil.JAVA);

    // Setup C++ and Java breakpoints. C++ breakpoint won't be hit here.
    openAndToggleBreakPoints(ideFrameFixture, "app/src/main/jni/native-lib.c", "return (*env)->NewStringUTF(env, message);");
    openAndToggleBreakPoints(ideFrameFixture, "app/src/main/java/com/example/basiccmakeapp/MainActivity.java", "setContentView(tv);");

    ideFrameFixture.debugApp(DEBUG_CONFIG_NAME)
        .selectDevice(emulator.getDefaultAvdName())
        .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrameFixture);
    waitForJavaDebuggerSessionStart(debugToolWindowFixture);

    //Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[] expectedPatterns = new String[]{
      variableToSearchPattern("s", "\"Success. Sum = 55, Product = 3628800, Quotient = 512\""),
    };
    checkAppIsPaused(ideFrameFixture, expectedPatterns);

    assertThat(debugToolWindowFixture.getDebuggerContent(DEBUG_CONFIG_NAME)).isNotNull();
    assertThat(debugToolWindowFixture.getContentCount() == 1).isTrue();

    stopDebugSession(debugToolWindowFixture);
  }

  private static void waitForJavaDebuggerSessionStart(@NotNull DebugToolWindowFixture debugToolWindowFixture) {
    final Pattern LAUNCH_APP_PATTERN = Pattern.compile(".*Launching app.*", Pattern.DOTALL);
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(LAUNCH_APP_PATTERN), 10);

    Wait.seconds(60).expecting("Debugger tab is selected.")
        .until(() -> debugToolWindowFixture.isTabSelected("Debugger"));
  }

}
