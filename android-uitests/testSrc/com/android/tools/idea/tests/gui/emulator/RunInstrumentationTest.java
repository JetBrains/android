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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(GuiTestRemoteRunner.class)
public class RunInstrumentationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private final EmulatorTestRule emulator = new EmulatorTestRule(false);
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  private static final String APP_NAME = "app";
  private static final String INSTRUMENTED_TEST_CONF_NAME = "instrumented_test";
  private static final String ANDROID_INSTRUMENTED_TESTS = "Android Instrumented Tests";
  private static final Pattern INSTRUMENTED_TEST_OUTPUT = Pattern.compile(
    ".*adb shell am instrument .*AndroidJUnitRunner.*Tests ran to completion.*", Pattern.DOTALL);

  /**
   * To verify that instrumentation tests can be added and executed.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: c8fc3fd5-1a7d-405d-974f-5e4f0b42e168
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Create a new project
   *   3. Create an avd
   *   4. Open the default instrumented test example
   *   5. Open Run/Debug Configuration Settings
   *   6. Click on the "+" button and select Android Instrumented Tests
   *   7. Add a name to the test
   *   8. Select the app module and click OK"
   *   9. Run "ExampleInstrumentedTest" with test configuration created previously
   *   Verify:
   *   1. Test runs successfully by checking the output of running the instrumented test.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/80421743
  @Test
  public void runInstrumentationTest() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("InstrumentationTest");
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    String avdName = EmulatorGenerator.ensureDefaultAvdIsCreated(ideFrameFixture.invokeAvdManager());

    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
                                   .clickAddNewConfigurationButton()
                                   .selectConfigurationType(ANDROID_INSTRUMENTED_TESTS)
                                   .enterAndroidInstrumentedTestConfigurationName(INSTRUMENTED_TEST_CONF_NAME)
                                   .selectModuleForAndroidInstrumentedTestsConfiguration(APP_NAME)
                                   .clickOk();

    ideFrameFixture.runApp(INSTRUMENTED_TEST_CONF_NAME)
                   .selectDevice(avdName)
                   .clickOk();

    // Wait for background tasks to finish before requesting Run Tool Window. Otherwise Run Tool Window won't activate.
    guiTest.waitForBackgroundTasks();

    ideFrameFixture.getRunToolWindow().findContent(INSTRUMENTED_TEST_CONF_NAME)
                   .waitForOutput(new PatternTextMatcher(INSTRUMENTED_TEST_OUTPUT), 120);
  }
}
