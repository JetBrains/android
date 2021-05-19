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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateAPKProjectTest extends DebuggerTestBase {
  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() ->
    new AvdSpec.Builder()
  );

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  @Before
  public void setup() throws IOException {
    // An ~/ApkProjects directory will show us a dialog in the middle of the test
    // to overwrite the directory. Delete the directory now so it won't trip the test.
    CreateAPKProjectTestUtil.removeApkProjectsDirectory();

    DebuggerTestUtil.setupSpecialSdk(avdRule);
    DebuggerTestUtil.symlinkLldb();
  }

  /**
   * Verifies APKs built locally can be debugged with the Java debugger
   * and native debugger.
   *
   * <p>TT ID: eb372dee-1f04-48d8-95f8-bdac7484913d
   *
   * <pre>
   *   Test steps:
   *   1. Import ApkDebug project to build the APK locally.
   *   2. Open the prebuilt APK file to create an APK profiling and debugging project.
   *   3. Open the native library editor and attach C and C++ source code.
   *   4. Open the DemoActivity smali file to attach the Java source code.
   *   5. Create an emulator.
   *   6. Set some breakpoints in a Java source file and C source file.
   *   7. Launch the application in debug mode on the emulator.
   *   8. Check if the breakpoints are hit in the debug tool window.
   *   Verify:
   *   1. Java breakpoint is hit by the Java debugger.
   *   2. Native breakpoints are hit by the native debugger.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void debugLocallyBuiltApk() throws Exception {
    File projectRoot = buildApkLocally("ApkDebug");

    CreateAPKProjectTestUtil.profileOrDebugApk(guiTest, new File(projectRoot, "app/build/outputs/apk/debug/app-x86-debug.apk"));

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    EditorFixture editor = ideFrame.getEditor();

    File debugSymbols = new File(projectRoot, "app/build/intermediates/cmake/debug/obj/x86/libsanangeles.so");
    editor.open("lib/x86/libsanangeles.so")
      .getLibrarySymbolsFixture()
      .addDebugSymbols(debugSymbols);
    guiTest.waitForBackgroundTasks();

    // Add Java sources after adding native library debugging symbols due to b/62476714
    CreateAPKProjectTestUtil.attachJavaSources(ideFrame, new File(projectRoot, "app/src/main/java"));
    CreateAPKProjectTestUtil.waitForJavaFileToShow(editor);

    VirtualFile demoActivity = VfsUtil.findFileByIoFile(
      new File(projectRoot, "app/src/main/java/com/example/SanAngeles/DemoActivity.java"),
      true);

    openAndToggleBreakPoints(ideFrame, demoActivity, "super.onCreate(savedInstanceState);");

    VirtualFile cFile = VfsUtil.findFileByIoFile(
      new File(projectRoot, "app/src/main/cpp/app-android.c"),
      true);

    openAndToggleBreakPoints(
      ideFrame,
      cFile,
      "_resume(); // BREAKPOINT MARKING COMMENT");

    String debugConfigName = "app-x86-debug";

    ideFrame.debugApp(debugConfigName, avdRule.getMyAvd().getName());

    String debugWindowJava = "app-x86-debug-java";
    DebugToolWindowFixture debugWindow = ideFrame.getDebugToolWindow();
    Wait.seconds(EMULATOR_LAUNCH_WAIT_SECONDS)
      .expecting("emulator with the app launched in debug mode")
      .until(() -> {
        if (debugWindow.getContentCount() < 2) {
          return false;
        }

        return debugWindow.getDebuggerContent(debugWindowJava) != null;
      });

    debugWindow.waitForBreakPointHit();
    String[] expectedPattern = {
      variableToSearchPattern("savedInstanceState", "null"),
      variableToSearchPattern("mGLView", "null")
    };

    checkAppIsPaused(ideFrame, expectedPattern, debugWindowJava);

    debugWindow.pressResumeProgram();

    expectedPattern = new String[] {
      variableToSearchPattern("gAppAlive", "int", "1"),
      variableToSearchPattern("sDemoStopped", "int", "0")
    };

    checkAppIsPaused(ideFrame, expectedPattern, debugConfigName);

    debugWindow.pressResumeProgram();

    stopDebugSession(debugWindow, debugConfigName);
  }

  @NotNull
  private File buildApkLocally(@NotNull String apkProjectToImport) throws IOException {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish(apkProjectToImport, Wait.seconds(120));

    ideFrame.invokeAndWaitForBuildAction("Build", "Build Bundle(s) / APK(s)", "Build APK(s)");

    File projectRoot = ideFrame.getProjectPath();

    // We will have another window opened for the APK project. Close this window
    // so we don't have to manage two windows.
    ideFrame.closeProject();

    return projectRoot;
  }
}
