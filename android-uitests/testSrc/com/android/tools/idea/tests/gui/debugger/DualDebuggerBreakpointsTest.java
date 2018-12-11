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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class DualDebuggerBreakpointsTest extends DebuggerTestBase {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES).settingNdkPath();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String C_FILE_NAME = "app/src/main/jni/native-lib.c";
  private static final String C_BP_LINE = "return (*env)->NewStringUTF(env, message);";
  private static final String JAVA_FILE_NAME = "app/src/main/java/com/example/basiccmakeapp/MainActivity.java";
  private static final String JAVA_BP_LINE = "setContentView(tv);";

  @Before
  public void setUp() throws Exception {
    guiTest.importProject("debugger/BasicCmakeAppForUI");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish(Wait.seconds(60));

    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
  }

  /**
   * Verifies that instant run hot swap works as expected on a C++ support project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 54d17691-48b8-4bf2-9ac2-9ff179327418
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI.
   *   2. Select auto debugger on Edit Configurations dialog.
   *   3. Set breakpoints both in Java and C++ code.
   *   4. Debug on a device running M or earlier.
   *   5. When the C++ breakpoint is hit, verify variables and resume
   *   6. When the Java breakpoint is hit, verify variables
   *   7. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)  // b/77970753
  public void dualDebuggerBreakpoints() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    DebuggerTestUtil.setDebuggerType(ideFrameFixture, DebuggerTestUtil.DUAL);

    // Setup C++ and Java breakpoints.
    openAndToggleBreakPoints(ideFrameFixture, C_FILE_NAME, C_BP_LINE);
    openAndToggleBreakPoints(ideFrameFixture, JAVA_FILE_NAME, JAVA_BP_LINE);

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(ideFrameFixture, guiTest, DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[] expectedPatterns = new String[]{
      variableToSearchPattern("sum_of_10_ints", "int", "55"),
      variableToSearchPattern("product_of_10_ints", "int", "3628800"),
      variableToSearchPattern("quotient", "int", "512"),
    };
    checkAppIsPaused(ideFrameFixture, expectedPatterns);
    resume("app", ideFrameFixture);

    expectedPatterns = new String[]{
      variableToSearchPattern("s", "\"Success. Sum = 55, Product = 3628800, Quotient = 512\""),
    };

    checkAppIsPaused(ideFrameFixture, expectedPatterns, "app-java");
    assertThat(debugToolWindowFixture.getDebuggerContent("app-java")).isNotNull();
    // TODO Stop the session.
  }
}
