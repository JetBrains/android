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
package com.android.tools.idea.tests.gui.debugger;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestUtils;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.fest.swing.edt.GuiTask;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
// TODO: Remove dependency on DebuggerTestBase
public class DualDebuggerBreakpointsTest extends DebuggerTestBase {

  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() ->
    new AvdSpec.Builder()
  );

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  private static final String C_FILE_NAME = "app/src/main/jni/native-lib.c";
  private static final String C_BP_LINE = "return (*env)->NewStringUTF(env, message);";
  private static final String JAVA_FILE_NAME = "app/src/main/java/com/example/basiccmakeapp/MainActivity.java";
  private static final String JAVA_BP_LINE = "setContentView(tv);";

  /**
   * GuiTestRule sets the SDK. There is currently no way to override or prevent that behavior.
   * This workaround is to set the customized SDK that includes the emulator and system images
   * necessary for this test.
   */
  @Before
  public void setupSpecialSdk() {
    GuiTask.execute(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      IdeSdks.getInstance().setAndroidSdkPath(avdRule.getGeneratedSdkLocation());
    }));
  }

  @Before
  public void symlinkLldb() throws IOException {
    if (TestUtils.runningFromBazel()) {
      // tools/idea/bin is already symlinked to a directory that is outside the writable directory.
      // We need to write underneath tools/idea/bin, so we remove the symlink and create a copy of
      // the real tools/idea/bin.
      File tmpDir = new File(System.getenv("TEST_TMPDIR"));
      File toolsIdeaBin = new File(tmpDir, "tools/idea/bin");
      File realToolsIdeaBin = toolsIdeaBin.getCanonicalFile();
      toolsIdeaBin.delete();
      toolsIdeaBin.mkdirs();
      FileUtil.copyDir(realToolsIdeaBin, toolsIdeaBin);

      String srcDir = System.getenv("TEST_SRCDIR");
      String workspaceName = System.getenv("TEST_WORKSPACE");
      File lldbParent = new File(srcDir, workspaceName);

      File commonLldbSrc = new File(lldbParent, "prebuilts/tools/common/lldb");
      // Also create a copy of LLDB, since we will need to modify the contents of
      // tools/idea/bin/lldb.
      File commonLldbDest = new File(toolsIdeaBin, "lldb");
      FileUtil.copyDir(commonLldbSrc, commonLldbDest);

      File lldbLibSrc = new File(lldbParent, "prebuilts/tools/linux-x86_64/lldb");
      File lldbLibDest = new File(toolsIdeaBin, "lldb");
      FileUtil.copyDir(lldbLibSrc, lldbLibDest);

      File pythonSrc = new File(lldbParent, "prebuilts/python/linux-x86/lib/python2.7");
      File pythonDest = new File(toolsIdeaBin, "lldb/lib/python2.7");
      FileUtil.copyDir(pythonSrc, pythonDest);
    }
  }

  /**
   * Verifies that instant run hot swap works as expected on a C++ support project.
   * Verify Java and C++ breakpoints trigger.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 54d17691-48b8-4bf2-9ac2-9ff179327418
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI.
   *   2. Select auto debugger on Edit Configurations dialog.
   *   3. Set breakpoints both in Java and C++ code.
   *   4. Debug on a device running API 28 or earlier
   *   5. When the C++ breakpoint is hit, verify variables and resume
   *   6. When the Java breakpoint is hit, verify variables
   *   7. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)  // b/77970753
  public void dualDebuggerBreakpoints() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/BasicCmakeAppForUI");

    // TODO should we need to modify the debugger type? Shouldn't it be AUTO?
    DebuggerTestUtil.setDebuggerType(ideFrame, DebuggerTestUtil.DUAL);

    // Setup C++ and Java breakpoints.
    openAndToggleBreakPoints(ideFrame, C_FILE_NAME, C_BP_LINE);
    openAndToggleBreakPoints(ideFrame, JAVA_FILE_NAME, JAVA_BP_LINE);

    String avdName = avdRule.getMyAvd().getName();
    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(ideFrame, guiTest, DEBUG_CONFIG_NAME, avdName);

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[] expectedPatterns = new String[]{
      variableToSearchPattern("sum_of_10_ints", "int", "55"),
      variableToSearchPattern("product_of_10_ints", "int", "3628800"),
      variableToSearchPattern("quotient", "int", "512"),
    };
    checkAppIsPaused(ideFrame, expectedPatterns);
    resume("app", ideFrame);

    expectedPatterns = new String[]{
      variableToSearchPattern("s", "\"Success. Sum = 55, Product = 3628800, Quotient = 512\""),
    };

    checkAppIsPaused(ideFrame, expectedPatterns, "app-java");
    assertThat(debugToolWindowFixture.getDebuggerContent("app-java")).isNotNull();

    debugToolWindowFixture.findContent("app-java").waitForStopClick();
  }
}
