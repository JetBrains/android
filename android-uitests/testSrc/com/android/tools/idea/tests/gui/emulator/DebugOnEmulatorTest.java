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

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class DebugOnEmulatorTest {

  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() -> {
    // TODO avoid API 24 due to b/92112542
    AvdSpec.Builder builder = new AvdSpec.Builder();
    builder.setSystemImageSpec(
      new ChooseSystemImageStepFixture.SystemImage("Marshmallow", "23", "x86", "Android 6.0")
    );

    return builder;
  });
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  private static final String APP_NAME = "app";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(
    ".*adb shell am start .*google\\.simpleapplication.*", Pattern.DOTALL);
  private static final Pattern DEBUG_OUTPUT = Pattern.compile(".*Connected to the target VM.*", Pattern.DOTALL);

  /**
   * The SDK used for this test requires the emulator and the system images to be
   * available. The emulator and system images are not available in the prebuilts
   * SDK. The AvdTestRule should generate such an SDK for us, but we need to set
   * the generated SDK as the SDK to use for our test.
   *
   * Unfortunately, GuiTestRule can overwrite the SDK we set in AvdTestRule, so
   * we need to set this in a place after GuiTestRule has been applied.
   */
  @Before
  public void setupSpecialSdk() {
    GuiTask.execute(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      IdeSdks.getInstance().setAndroidSdkPath(avdRule.getGeneratedSdkLocation());
    }));
  }

  /**
   * Verifies that debugger can be invoked on an application by setting breakpoints
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: b9daba2b-067f-434b-91b2-c02197ac1521
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. Open the default activity class file
   *   3. In the OnCreate method, add a breakpoint
   *   4. Click on Debug project
   *   5. Select a running emulator
   *   Verify:
   *   The application is deployed on the emulator/device and the breakpoint is hit when the first screen loads
   *   </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void debugOnEmulator() throws IOException {
    try {
      guiTest.importSimpleApplication();
    } catch(WaitTimedOutError ignore) {
      // Ignore timeouts. QA sanity tests do not care about timeouts
      GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));
    }

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    String avdName = avdRule.getMyAvd().getName();

    ideFrameFixture.debugApp("app", avdName);

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ideFrameFixture.getDebugToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);
    ideFrameFixture.getDebugToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(DEBUG_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);

    ideFrameFixture.stopApp();
  }
}
