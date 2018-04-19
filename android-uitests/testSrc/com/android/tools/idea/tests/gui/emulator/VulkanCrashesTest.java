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
package com.android.tools.idea.tests.gui.emulator;

import com.android.tools.idea.tests.gui.debugger.DebuggerTestBase;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.openapi.wm.impl.content.ContentTabLabelFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class VulkanCrashesTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private final EmulatorTestRule emulator = new EmulatorTestRule(false);
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  private static final String APP_NAME = "app";
  private static final String FATAL_SIGNAL_11_OR_6 = ".*SIGSEGV.*|.*SIGABRT.*";

  /**
   * To verify app crashes if vulkan graphics is not supported.
   * <p>
   * The ideal test would launch the app on a real device (Nexus 5X or 6P). Since there
   * if no current framework support to run the app on a real device, this test reverses
   * the scenario and verifies the app will crash when the vulcan graphics card is not
   * present on the emulator when running the app.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 62320838-5ab1-4808-9a56-11e1fe349e1a
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import VulkanCrashes project.
   *   3. Navigate to the downloaded vulkan directory.
   *   3. Compile and run app on the emulator (Nexus 6P).
   *   Verify:
   *   1. Application crashes in the emulator.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void vulkanCrashes() throws IOException, ClassNotFoundException {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("VulkanCrashes");

    String avdName = EmulatorGenerator.ensureAvdIsCreated(
      ideFrameFixture.invokeAvdManager(),
      new AvdSpec.Builder()
        .setHardwareProfile("Nexus 6P")
        .setSystemImageGroup(AvdSpec.SystemImageGroups.X86)
        .setSystemImageSpec(
          new ChooseSystemImageStepFixture.SystemImage("Nougat", "24", "x86", "Android 7.0")
        )
        .build()
    );

    // The app must run under the debugger, otherwise there is a race condition where
    // the app may crash before Android Studio can connect to the console.
    ideFrameFixture
      .debugApp(APP_NAME)
      .selectDevice(avdName)
      .clickOk();

    // Wait for background tasks to finish before requesting Debug Tool Window. Otherwise Debug Tool Window won't activate.
    guiTest.waitForBackgroundTasks();

    // wait for both debugger tabs to be available and visible
    ContentTabLabelFixture.find(
      ideFrameFixture.robot(),
      Matchers.byText(BaseLabel.class, APP_NAME),
      EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS
    );
    ContentTabLabelFixture.find(
      ideFrameFixture.robot(),
      Matchers.byText(BaseLabel.class, APP_NAME + "-java"),
      EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS
    );

    // wait for native debugger to receive a fatal signal. Use DebuggerTestBase to check for when the app has paused.
    String[] expectedDebuggerPatterns = {
      "Signal = (SIGABRT|SIGSEGV).*"
    };
    DebuggerTestBase.checkAppIsPaused(ideFrameFixture, expectedDebuggerPatterns, APP_NAME);
    // Look for text indicating a crash. Check for both SIGSEGV and SIGABRT since they are both given in some cases.
    ExecutionToolWindowFixture.ContentFixture contentWindow = ideFrameFixture.getDebugToolWindow().findContent(APP_NAME);
    contentWindow.waitForOutput(new PatternTextMatcher(Pattern.compile(FATAL_SIGNAL_11_OR_6, Pattern.DOTALL)), 10);
    contentWindow.stop();
  }
}
