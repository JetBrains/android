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

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture.ContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RunToolWindowFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class BuildAndRunCMakeProjectTest {
  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() ->
    new AvdSpec.Builder()
  );

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  private static final String RUN_CONFIG_NAME = "app";

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
  @RunIn(TestGroup.FAST_BAZEL)
  public void testBuildAndRunCMakeProject() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/BasicCmakeAppForUI");

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    ideFrame.invokeAndWaitForBuildAction("Build", "Rebuild Project");

    ideFrame.runApp(RUN_CONFIG_NAME, avdRule.getMyAvd().getName());

    RunToolWindowFixture runToolWindowFixture = new RunToolWindowFixture(ideFrame);
    Pattern LAUNCH_APP_PATTERN = Pattern.compile(".*Launching 'app'.*", Pattern.DOTALL);
    Pattern CONNECTED_APP_PATTERN = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);
    ContentFixture contentFixture = runToolWindowFixture.findContent(RUN_CONFIG_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(LAUNCH_APP_PATTERN), 10);
    contentFixture.waitForOutput(new PatternTextMatcher(CONNECTED_APP_PATTERN), 60);
    contentFixture.waitForStopClick();
    contentFixture.waitForExecutionToFinish();
  }
}
