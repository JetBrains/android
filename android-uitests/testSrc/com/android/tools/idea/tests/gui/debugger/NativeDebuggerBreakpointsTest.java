/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class NativeDebuggerBreakpointsTest extends DebuggerTestBase {
  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() ->
    new AvdSpec.Builder()
  );

  private static final String C_FILE_NAME = "app/src/main/jni/native-lib.c";
  private static final String C_BP_LINE = "return (*env)->NewStringUTF(env, message);";
  private static final String JAVA_FILE_NAME = "app/src/main/java/com/example/basiccmakeapp/MainActivity.java";
  private static final String JAVA_BP_LINE = "setContentView(tv);";

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  @Before
  public void setupSdkAndLldb() throws IOException {
    DebuggerTestUtil.setupSpecialSdk(avdRule);
    DebuggerTestUtil.symlinkLldb();
  }

  /**
   * Verify native debugger is attached to a running process.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 45e4c839-5c55-40f7-8264-4fe75ee02624
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI.
   *   2. Select Native debugger on Edit Configurations dialog.
   *   3. Set breakpoints both in Java and C++ code.
   *   4. Debug on a device running M or earlier.
   *   5. Verify that only native debugger is attached and running.
   *   6. Stop debugging.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void breakpointsWithNativeDebugger() throws Exception {
    guiTest.importProject("debugger/BasicCmakeAppForUI");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish(Wait.seconds(60));

    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    DebuggerTestUtil.setDebuggerType(projectFrame, DebuggerTestUtil.NATIVE);

    // Set breakpoint in java code, but it wouldn't be hit when it is native debugger type.
    openAndToggleBreakPoints(projectFrame, JAVA_FILE_NAME, JAVA_BP_LINE);

    openAndToggleBreakPoints(projectFrame, C_FILE_NAME, C_BP_LINE);

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(projectFrame, guiTest, DEBUG_CONFIG_NAME, avdRule.getMyAvd().getName());

    String[] expectedPatterns = new String[]{
      variableToSearchPattern("sum_of_10_ints", "int", "55"),
      variableToSearchPattern("product_of_10_ints", "int", "3628800"),
      variableToSearchPattern("quotient", "int", "512")
    };
    checkAppIsPaused(projectFrame, expectedPatterns);
    assertThat(debugToolWindowFixture.getDebuggerContent(DebuggerTestUtil.JAVA_DEBUGGER_CONF_NAME)).isNull();
    stopDebugSession(debugToolWindowFixture);
  }
}
