/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.android.tools.idea.tests.util.NotMatchingPatternMatcher;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class BasicNativeDebuggerTest extends DebuggerTestBase {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES).settingNdkPath();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String C_FILE_NAME = "app/src/main/jni/native-lib.c";
  private static final String C_BP_LINE = "return (*env)->NewStringUTF(env, message);";

  @Before
  public void setUp() throws Exception {
    guiTest.importProject("debugger/BasicCmakeAppForUI");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish(Wait.seconds(60));

    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
  }

  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testNativeDebuggerCleanWhileDebugging() throws  Exception {
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    DebuggerTestUtil.setDebuggerType(projectFrame, DebuggerTestUtil.NATIVE);

    openAndToggleBreakPoints(projectFrame, C_FILE_NAME, C_BP_LINE);

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(projectFrame, guiTest, DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());

    projectFrame.invokeMenuPath("Build", "Clean Project");
    MessagesFixture messagesFixture = MessagesFixture.findByTitle(guiTest.robot(), "Terminate debugging");
    // Cancel and check that the debugging session is still happening.
    messagesFixture.clickCancel();
    checkAppIsPaused(projectFrame, new String[]{});

    projectFrame.invokeMenuPath("Build", "Clean Project");
    messagesFixture = MessagesFixture.findByTitle(guiTest.robot(), "Terminate debugging");
    // Click okay and check that the debugger has been killed.
    messagesFixture.click("Terminate");
    assertThat(debugToolWindowFixture.getDebuggerContent("app-native")).isNull();
  }

  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testNativeDebuggerNewLibraryWhileDebugging() throws Exception {
    final IdeFrameFixture projectFrame = guiTest.ideFrame();
    DebuggerTestUtil.setDebuggerType(projectFrame, DebuggerTestUtil.NATIVE);
    openAndToggleBreakPoints(projectFrame, C_FILE_NAME, C_BP_LINE);

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(projectFrame, guiTest, DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());

    // Add a new Android Library.  Note that this needs the path to Kotlin defined in the test's
    // JVM arguments.  See go/studio-testing-pitfalls for information.
    projectFrame.invokeMenuPath("File", "New", "New Module...");
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextToAndroidLibrary()
      .wizard()
      .clickFinish();

    MessagesFixture messagesFixture = MessagesFixture.findByTitle(guiTest.robot(), "Terminate debugging");
    // Cancel and check that the debugging session is still happening.
    messagesFixture.clickCancel();
    checkAppIsPaused(projectFrame, new String[]{});
    stopDebugSession(debugToolWindowFixture);

    projectFrame.debugApp(DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());
    debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    waitForSessionStart(debugToolWindowFixture);

    projectFrame.invokeMenuPath("File", "New", "New Module...");
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextToAndroidLibrary()
      .wizard()
      .clickFinish();

    messagesFixture = MessagesFixture.findByTitle(guiTest.robot(), "Terminate debugging");
    // Click okay and check that the debugger has been killed.
    messagesFixture.click("Terminate");
    assertThat(debugToolWindowFixture.getDebuggerContent("app-native")).isNull();
  }

  private void waitUntilDebugConsoleCleared(DebugToolWindowFixture debugToolWindowFixture) {
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new NotMatchingPatternMatcher(DEBUGGER_ATTACHED_PATTERN), 10);
  }

}
