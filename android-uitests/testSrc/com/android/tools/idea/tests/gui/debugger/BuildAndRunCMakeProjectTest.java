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

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture.ContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RunToolWindowFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class BuildAndRunCMakeProjectTest {
  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String RUN_CONFIG_NAME = "app";

  /**
   * Verifies that C-Make project can be built and deployed.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 9b67ccb3-a487-4dfd-9ad1-73e0295f249e
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI.
   *   2. Create an emulator.
   *   3. Rebuild project and wait for build to finish.
   *   4. Run on the emulator created in step 2.
   *   Verify:
   *   Project should build and run successfully on emulator.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testBuildAndRunCMakeProject() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicCmakeAppForUI");

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    emulator.createDefaultAVD(ideFrame.invokeAvdManager());

    ideFrame.invokeMenuPath("Build", "Rebuild Project").waitForBuildToFinish(BuildMode.REBUILD);

    ideFrame.runApp(RUN_CONFIG_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    RunToolWindowFixture runToolWindowFixture = new RunToolWindowFixture(ideFrame);
    Pattern LAUNCH_APP_PATTERN = Pattern.compile(".*Launching app.*", Pattern.DOTALL);
    Pattern CONNECTED_APP_PATTERN = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);
    ContentFixture contentFixture = runToolWindowFixture.findContent(RUN_CONFIG_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(LAUNCH_APP_PATTERN), 10);
    contentFixture.waitForOutput(new PatternTextMatcher(CONNECTED_APP_PATTERN), 60);
  }
}
