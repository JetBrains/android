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
package com.android.tools.idea.tests.gui.espresso;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RecordingDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.TestClassNameInputDialogFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class EspressoRecorderTest {

  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);
  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() ->
    new AvdSpec.Builder()
  );
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  private static final String APP_NAME = "MyActivityTest";
  private static final String TEST_RECORDER_APP = "TestRecorderapp";
  private static final Pattern DEBUG_OUTPUT =
    Pattern.compile(".*google\\.simpleapplication\\.test.*Connecting to google\\.simpleapplication.*", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*adb shell am instrument.*google\\.simpleapplication\\.MyActivityTest.*Tests ran to completion.*", Pattern.DOTALL);

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
   * To verify espresso adds dependencies after recording in new project
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 908a36f6-4e89-4031-8b3d-c2a75ddc7f08
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. Run | Record Espresso Test
   *   3. Wait for recording dialog and click OK
   *   5. Wait for test class name input dialog and click OK
   *   6. Click yes to add missing Espresso dependencies
   *   7. Run test
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY_BAZEL) // http://b/77635374
  @Test
  public void addDependencyOnFly() throws Exception {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    String avdName = avdRule.getMyAvd().getName();

    ideFrameFixture
      .recordEspressoTest(avdName)
      .getDebugToolWindow()
      .findContent(TEST_RECORDER_APP)
      .waitForOutput(new PatternTextMatcher(DEBUG_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);

    RecordingDialogFixture.find(guiTest.robot()).clickOk();
    TestClassNameInputDialogFixture.find(guiTest.robot()).clickOk();
    ideFrameFixture.actAndWaitForGradleProjectSyncToFinish(
      it ->
        MessagesFixture
          .findByTitle(guiTest.robot(), "Missing or obsolete Espresso dependencies")
          .clickYes());

    // Run Android test.
    // The test menu item does not appear in the menu until we interact with the editor
    // This is a minor bug: https://issuetracker.google.com/71516507
    // Generate a click in the editor:
    try {
      ideFrameFixture.getEditor()
        .waitUntilErrorAnalysisFinishes()
        .moveBetween("public class ", "MyActivityTest");
    } catch(WaitTimedOutError ignored) {
      // We do not care if our cursor is not where it needs to be. We
      // just needed a click inside the editor.
    }

    Ref<JListFixture> popupList = new Ref<>();
    Wait.seconds(20).expecting("The instrumentation test is ready").until(() -> {
      ideFrameFixture.invokeMenuPath("Run", "Run...");
      JListFixture listFixture = new JListFixture(guiTest.robot(), GuiTests.waitForPopup(guiTest.robot()));
      if (Arrays.asList(listFixture.contents()).contains("Wrapper[MyActivityTest]")) {
        popupList.set(listFixture);
        return true;
      } else {
        guiTest.robot().pressAndReleaseKeys(KeyEvent.VK_ESCAPE);
        return false;
      }
    });

    popupList.get().clickItem("Wrapper[MyActivityTest]");

    ideFrameFixture
      .selectDevice(avdName)
      .getRunToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);
  }
}
