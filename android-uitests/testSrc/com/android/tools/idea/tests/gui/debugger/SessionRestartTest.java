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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProcessRunningDialogFixture;
import com.android.tools.idea.tests.util.NotMatchingPatternMatcher;
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
public class SessionRestartTest extends DebuggerTestBase {
  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() ->
    new AvdSpec.Builder()
  );

  private static final String C_FILE_NAME = "app/src/main/jni/native-lib.c";
  private static final String C_BP_LINE = "return (*env)->NewStringUTF(env, message);";

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  @Before
  public void setupSdkAndLldb() throws IOException {
    DebuggerTestUtil.setupSpecialSdk(avdRule);
    DebuggerTestUtil.symlinkLldb();
  }

  /**
   * <p>TT ID: TODO this test case needs a TT ID.
   *
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void testSessionRestart() throws Exception {
    guiTest.importProject("debugger/BasicCmakeAppForUI");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish(Wait.seconds(60));
    final IdeFrameFixture projectFrame = guiTest.ideFrame();
    DebuggerTestUtil.setDebuggerType(projectFrame, DebuggerTestUtil.AUTO);
    openAndToggleBreakPoints(projectFrame, C_FILE_NAME, C_BP_LINE);

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(projectFrame, guiTest, DEBUG_CONFIG_NAME, avdRule.getMyAvd().getName());

    projectFrame.debugApp(DEBUG_CONFIG_NAME, avdRule.getMyAvd().getName());

    ProcessRunningDialogFixture.find(projectFrame).clickTerminate();

    waitUntilDebugConsoleCleared(debugToolWindowFixture);
    waitForSessionStart(debugToolWindowFixture);
    stopDebugSession(debugToolWindowFixture);
  }

  private void waitUntilDebugConsoleCleared(DebugToolWindowFixture debugToolWindowFixture) {
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new NotMatchingPatternMatcher(DEBUGGER_ATTACHED_PATTERN), 10);
  }
}
