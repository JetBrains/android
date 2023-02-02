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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ImportAndRunInstantAppTest {
  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() -> {
    AvdSpec.Builder builder = new AvdSpec.Builder();
    builder.setSystemImageSpec(
      new ChooseSystemImageStepFixture.SystemImage("Pie", "28", "x86", "Android 9.0 (Google APIs)")
    );
    return builder;
  });

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

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
   * Verify imported instant apps can be deployed to an emulator running API 26 or newer.
   *
   * <p>TT ID: 56be2a70-25a2-4b1f-9887-c19073874aa2
   *
   * <pre>
   *   Test steps:
   *   1. Import an instant app project.
   *   2. Set up an emulator running API 28.
   *   3. Run the instant app configuration.
   *   Verify:
   *   1. Check if the run tool window appears.
   *   2. Check if the "Connected to process" message appears in the run tool window.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void importAndRunInstantApp() throws Exception {
    String runConfigName = "app";

    guiTest.importProjectAndWaitForProjectSyncToFinish("InstantAppsService", Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));
    GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));

    guiTest.ideFrame().runApp(runConfigName, avdRule.getMyAvd().getName(), Wait.seconds(TimeUnit.MINUTES.toSeconds(10)));

    Pattern CONNECTED_APP_PATTERN = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);
    ExecutionToolWindowFixture.ContentFixture runWindow = guiTest.ideFrame().getRunToolWindow().findContent(runConfigName);
    runWindow.waitForOutput(new PatternTextMatcher(CONNECTED_APP_PATTERN), TimeUnit.MINUTES.toSeconds(5));

    runWindow.waitForStopClick();
  }
}