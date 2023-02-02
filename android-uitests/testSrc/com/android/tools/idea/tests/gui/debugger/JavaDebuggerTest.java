
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class JavaDebuggerTest extends DebuggerTestBase {
  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES).settingNdkPath();
  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() ->
    new AvdSpec.Builder()
  );

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  private static final String DEBUG_CONFIG_NAME = "app";

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
   * Verifies that Java Debugger works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: TODO: Wait for manual test case to be added.
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicJniAppForUI.
   *   2. Select Java debugger on Edit Configurations dialog.
   *   3. Set breakpoints both in Java and C++ code.
   *   4. Debug on a device running M or earlier.
   *   5. When the Java breakpoint is hit, verify variables and resume.
   *   6. C++ breakpoint is not hit.
   *   7. Stop debugging.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void testJavaDebugger() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/BasicCmakeAppForUI");
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    DebuggerTestUtil.setDebuggerType(ideFrameFixture, DebuggerTestUtil.JAVA);

    // Setup C++ and Java breakpoints. C++ breakpoint won't be hit here.
    openAndToggleBreakPoints(ideFrameFixture, "app/src/main/jni/native-lib.c", "return (*env)->NewStringUTF(env, message);");
    openAndToggleBreakPoints(ideFrameFixture, "app/src/main/java/com/example/basiccmakeapp/MainActivity.java", "setContentView(tv);");

    ideFrameFixture.debugApp(DEBUG_CONFIG_NAME, avdRule.getMyAvd().getName());

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrameFixture);
    waitForJavaDebuggerSessionStart(debugToolWindowFixture);

    //Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[] expectedPatterns = new String[]{
      variableToSearchPattern("s", "\"Success. Sum = 55, Product = 3628800, Quotient = 512\""),
    };
    checkAppIsPaused(ideFrameFixture, expectedPatterns);

    assertThat(debugToolWindowFixture.getDebuggerContent(DEBUG_CONFIG_NAME)).isNotNull();
    assertThat(debugToolWindowFixture.getContentCount() == 1).isTrue();

    stopDebugSession(debugToolWindowFixture);
  }

  private static void waitForJavaDebuggerSessionStart(@NotNull DebugToolWindowFixture debugToolWindowFixture) {
    final Pattern LAUNCH_APP_PATTERN = Pattern.compile(".*Launching 'app'.*", Pattern.DOTALL);
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(LAUNCH_APP_PATTERN), 10);

    Wait.seconds(60).expecting("Debugger tab is selected.")
        .until(() -> debugToolWindowFixture.isTabSelected("Debugger"));
  }

}
