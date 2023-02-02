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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
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
public class InstrumentationTest {
  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);
  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() ->
    new AvdSpec.Builder()
  );
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  private static final String APP_NAME = "KotlinInstrumentation.app";
  private static final String INSTRUMENTED_TEST_CONF_NAME = "kotlin_instrumented_test";
  private static final String ANDROID_INSTRUMENTED_TESTS = "Android Instrumented Tests";
  private static final Pattern INSTRUMENTED_TEST_OUTPUT = Pattern.compile(
    ".*adb shell am instrument .*AndroidJUnitRunner.*Tests ran to completion.*", Pattern.DOTALL);

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
   * Verifies default Instrumentation test case run without any errors.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d6dbbd96-722c-4a64-8768-cc9bd3b2af39
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import KotlinInstrumentation project and wait for project sync to finish.
   *   2. Create an avd.
   *   3. Open Run/Debug Configuration Settings.
   *   4. Click on the "+" button and select Android Instrumented Tests.
   *   5. Add a name to the test.
   *   6. Select the app module and click OK.
   *   7. Run "ExampleInstrumentedTest" with test configuration created previously.
   *   Verify:
   *   1. Test runs successfully by checking the output of running the instrumented test.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testInstrumentation() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish("KotlinInstrumentation");

    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
      .clickAddNewConfigurationButton()
      .selectConfigurationType(ANDROID_INSTRUMENTED_TESTS)
      .enterAndroidInstrumentedTestConfigurationName(INSTRUMENTED_TEST_CONF_NAME)
      .selectModuleForAndroidInstrumentedTestsConfiguration(APP_NAME)
      .clickOk();

    ideFrameFixture.runApp(INSTRUMENTED_TEST_CONF_NAME, avdRule.getMyAvd().getName());

    ideFrameFixture.getRunToolWindow().findContent(INSTRUMENTED_TEST_CONF_NAME)
      .waitForOutput(new PatternTextMatcher(INSTRUMENTED_TEST_OUTPUT), 120);
  }
}
