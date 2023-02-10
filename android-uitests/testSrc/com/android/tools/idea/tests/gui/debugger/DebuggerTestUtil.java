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
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.emulator.EmulatorGenerator;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;

public class DebuggerTestUtil {

  public static final String AUTO = "Detect Automatically";
  public static final String DUAL = "Dual (Java + Native)";
  public static final String NATIVE = "Native Only";
  public static final String JAVA = "Java Only";
  public static final String DEBUG_CONFIG_NAME = "app";
  public static final String JAVA_DEBUGGER_CONF_NAME = "app-java";

  public final static String ABI_TYPE_X86 = "x86";
  public final static String ABI_TYPE_X86_64 = "x86_64";

  private final static int GRADLE_SYNC_TIMEOUT = 60;

  static void setDebuggerType(@NotNull IdeFrameFixture ideFrameFixture, @NotNull String type) {
    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");

    EditConfigurationsDialogFixture.find(ideFrameFixture.robot())
      .selectDebuggerType(type)
      .clickOk();
  }

  public static DebugToolWindowFixture debugAppAndWaitForSessionToStart(@NotNull IdeFrameFixture ideFrameFixture,
                                                                        @NotNull GuiTestRule guiTest,
                                                                        @NotNull String configName,
                                                                        @NotNull String avdName) {
    return debugAppAndWaitForSessionToStart(ideFrameFixture, guiTest, configName, avdName, Wait.seconds(90));
  }

  public static DebugToolWindowFixture debugAppAndWaitForSessionToStart(@NotNull IdeFrameFixture ideFrameFixture,
                                                                        @NotNull GuiTestRule guiTest,
                                                                        @NotNull String configName,
                                                                        @NotNull String avdName,
                                                                        @NotNull Wait wait) {
    ideFrameFixture.debugApp(configName, avdName, wait);

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrameFixture);

    // Wait for "Debugger attached to process.*" to be printed on the app-native debug console.
    ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(configName);
    Pattern DEBUGGER_ATTACHED_PATTERN = Pattern.compile(".*Debugger attached to process.*", Pattern.DOTALL);
    contentFixture.waitForOutput(new PatternTextMatcher(DEBUGGER_ATTACHED_PATTERN), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);

    return debugToolWindowFixture;
  }

  public static void abiSplitApks(@NotNull GuiTestRule guiTest,
                                  @NotNull String abiType) throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("BasicCmakeAppForUI", Wait.seconds(GRADLE_SYNC_TIMEOUT));

    DebuggerTestUtil.setDebuggerType(ideFrame, DebuggerTestUtil.NATIVE);

    ideFrame.getEditor()
            .open("app/build.gradle", EditorFixture.Tab.EDITOR)
            .moveBetween("apply plugin: 'com.android.application'", "")
            .enterText("\n\nandroid.splits.abi.enable true")
            .invokeAction(EditorFixture.EditorAction.SAVE);

    ideFrame.requestProjectSyncAndWaitForSyncToFinish(Wait.seconds(GRADLE_SYNC_TIMEOUT));

    openAndToggleBreakPoints(ideFrame,
                             "app/src/main/jni/native-lib.c",
                             "return (*env)->NewStringUTF(env, message);");

    String expectedApkName = "";
    String avdName = "";
    if (abiType.equals(ABI_TYPE_X86)) {
      expectedApkName = "app-x86-debug.apk";
    } else if (abiType.equals(ABI_TYPE_X86_64)) {
      expectedApkName = "app-x86_64-debug.apk";
    } else {
      throw new RuntimeException("Not supported ABI type provided: " + abiType);
    }

    ChooseSystemImageStepFixture.SystemImage systemImageSpec = new ChooseSystemImageStepFixture.SystemImage(
      "Nougat",
      "24",
      abiType,
      "Android 7.0 (Google APIs)"
    );
    avdName = EmulatorGenerator.ensureAvdIsCreated(
      ideFrame.invokeAvdManager(),
      new AvdSpec.Builder()
        .setSystemImageGroup(AvdSpec.SystemImageGroups.X86)
        .setSystemImageSpec(systemImageSpec)
        .build()
    );

    DebuggerTestUtil.debugAppAndWaitForSessionToStart(ideFrame, guiTest, "app", avdName, Wait.seconds(180));

    ideFrame.stopApp();
    ProjectViewFixture.PaneFixture projectPane = ideFrame.getProjectView().selectProjectPane();

    final String apkNameRef = expectedApkName;
    Wait.seconds(30).expecting("The apk file is generated.").until(() -> {
      try {
        projectPane.clickPath("BasicCmakeAppForUI",
                              "app",
                              "build",
                              "intermediates",
                              "instant-run-apk",
                              "debug",
                              apkNameRef);
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });
  }

  /**
   * GuiTestRule sets the SDK. There is currently no way to override or prevent that behavior.
   * This workaround is to set the customized SDK that includes the emulator and system images
   * necessary for this test.
   */
  public static void setupSpecialSdk(@NotNull AvdTestRule avdRule) {
    GuiTask.execute(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      IdeSdks.getInstance().setAndroidSdkPath(avdRule.getGeneratedSdkLocation());
    }));
  }

  public static void symlinkLldb() throws IOException {
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

  public static void processToTest(@NotNull GuiTestRule guiTest,
                                   @NotNull AvdTestRule avdRule,
                                   @NotNull String debuggerType) throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/NdkHelloJni");
    DebuggerTestUtil.setDebuggerType(ideFrame, debuggerType);

    // Setup C++ and Java breakpoints.
    if (debuggerType.equals(DebuggerTestUtil.AUTO)) {
      // Don't set Java breakpoint.
    } else if (debuggerType.equals(DebuggerTestUtil.DUAL) || debuggerType.equals(DebuggerTestUtil.NATIVE)) {
      openAndToggleBreakPoints(ideFrame,
                               "app/src/main/java/com/example/hellojni/HelloJni.java",
                               "setContentView(tv);");
    } else {
      throw new RuntimeException("Not supported debugger type provide: " + debuggerType);
    }
    openAndToggleBreakPoints(ideFrame,
                             "app/src/main/cpp/hello-jni.c",
                             "return (*env)->NewStringUTF(env, \"ABI \" ABI \".\");");

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(ideFrame, guiTest, DEBUG_CONFIG_NAME, avdRule.getMyAvd().getName());

    // Setup the expected patterns to match the variable values displayed in Debug windows's
    // 'Variables' tab.
    String[] expectedPatterns = new String[]{
      DebuggerTestBase.variableToSearchPattern("kid_age", "int", "3"),
    };
    DebuggerTestBase.checkAppIsPaused(ideFrame, expectedPatterns);

    if (debuggerType.equals(DebuggerTestUtil.DUAL)) {
      DebuggerTestBase.resume(DEBUG_CONFIG_NAME, ideFrame);

      expectedPatterns = new String[]{
        DebuggerTestBase.variableToSearchPattern("s", "\"ABI x86.\""),
      };
      DebuggerTestBase.checkAppIsPaused(ideFrame, expectedPatterns, DebuggerTestUtil.JAVA_DEBUGGER_CONF_NAME);
    }

    if (debuggerType.equals(DebuggerTestUtil.AUTO)) {
      // Don't check Java debugger window for auto debugger here.
    } else if (debuggerType.equals(DebuggerTestUtil.DUAL)) {
      assertThat(debugToolWindowFixture.getDebuggerContent(DebuggerTestUtil.JAVA_DEBUGGER_CONF_NAME)).isNotNull();
    } else if (debuggerType.equals(DebuggerTestUtil.NATIVE)) {
      assertThat(debugToolWindowFixture.getDebuggerContent(DebuggerTestUtil.JAVA_DEBUGGER_CONF_NAME)).isNull();
    } else {
      throw new RuntimeException("Not supported debugger type provide: " + debuggerType);
    }

    if (debuggerType.equals(DebuggerTestUtil.AUTO) || debuggerType.equals(DebuggerTestUtil.NATIVE)) {
      DebuggerTestBase.stopDebugSession(debugToolWindowFixture);
    } else if (debuggerType.equals(DebuggerTestUtil.DUAL)) {
      ideFrame.stopAll();
    } else {
      throw new RuntimeException("Not supported debugger type provide: " + debuggerType);
    }
  }

  private static void openAndToggleBreakPoints(IdeFrameFixture ideFrame, String fileName, String... lines) {
    EditorFixture editor = ideFrame.getEditor().open(fileName);
    for (String line : lines) {
      editor.moveBetween("", line);
      editor.invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT);
    }
    editor.close();
  }
}
