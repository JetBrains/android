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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class InstantAppRunTest {
  private static final String O_AVD_NAME = "O dev under test";

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  /**
   * Verify instant apps can be deployed to an emulator running API 26 or newer.
   *
   * <p>TT ID: 84f8150d-0319-4e7e-b510-8227890aca3f
   *
   * <pre>
   *   Test steps:
   *   1. Import an instant app project.
   *   2. Set up an emulator running API 26.
   *   3. Run the instantapp run configuration.
   *   Verify:
   *   1. Check if the run tool window appears.
   *   2. Check if the "Connected to process" message appears in the run tool window.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void runSimpleInstantApp() throws Exception {
    String runConfigName = "instantapp";
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleInstantApp");

    emulator.createAVD(
      ideFrame.invokeAvdManager(),
      "x86 Images",
      new ChooseSystemImageStepFixture.SystemImage("O", "26", "x86", "Android 8.0 (Google APIs)"),
      O_AVD_NAME
    );

    ideFrame.runApp(runConfigName)
      .selectDevice(O_AVD_NAME)
      .clickOk();

    Pattern CONNECTED_APP_PATTERN = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);

    ExecutionToolWindowFixture.ContentFixture runWindow = ideFrame.getRunToolWindow().findContent(runConfigName);
    runWindow.waitForOutput(new PatternTextMatcher(CONNECTED_APP_PATTERN), TimeUnit.MINUTES.toSeconds(2));

    runWindow.waitForStopClick();
  }
}
