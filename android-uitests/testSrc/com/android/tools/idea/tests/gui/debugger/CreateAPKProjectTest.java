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

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.LibraryEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RunWith(GuiTestRunner.class)
public class CreateAPKProjectTest extends DebuggerTestBase {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  @Before
  public void removeExistingApkProjects() {
    // An ~/ApkProjects directory will show us a dialog in the middle of the test
    // to overwrite the directory. Delete the directory now so it won't trip the test.
    removeApkProjectsDirectory();
  }

  /**
   * Verifies source code directories are set for locally built APKs.
   *
   * <p>TT ID: 47e054af-29c2-4bfa-8410-e2f826452e78
   *
   * <pre>
   *   Test steps:
   *   1. Import ApkDebug.
   *   2. Build APK
   *   3. Close ApkDebug project window.
   *   4. Create APK debugging project with the locally built APK
   *   5. Open a .smali file to attach Java sources
   *   6. Open the native library to view the native library editor window.
   *   8. Attach debug symbols for the library.
   *   Verify:
   *   1. C and C++ code directories are attached to the native library
   *   2. Java source code files are added to the project tree.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // Bug: http://b/65172343
  public void createProjectFromLocallyBuiltApk() throws Exception {
    File projectRoot = buildApkLocally("ApkDebug");

    profileOrDebugApk(guiTest.welcomeFrame(), new File(projectRoot, "app/build/outputs/apk/debug/app-x86-debug.apk"));

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();
    attachJavaSources(ideFrame, new File(projectRoot, "app/src/main/java"));

    // need to wait since the editor doesn't open the java file immediately
    waitForJavaFileToShow(editor);

    File debugSymbols = new File(projectRoot, "app/build/intermediates/cmake/debug/obj/x86/libsanangeles.so");
    editor.open("lib/x86/libsanangeles.so")
      .getLibrarySymbolsFixture()
      .addDebugSymbols(debugSymbols);
    guiTest.waitForBackgroundTasks();

    List<ProjectViewFixture.NodeFixture> srcNodes = getNativeLibChildren(ideFrame, "libsanangeles");
    int numCppFolders = countOccurrencesOfFolderNameIn(srcNodes, "cpp");
    int numIncludeFolders = countOccurrencesOfFolderNameIn(srcNodes, "include");
    Assert.assertEquals(1, numCppFolders);
    Assert.assertEquals(2, numIncludeFolders);
  }

  /**
   * Verifies source code directories are set for APKs built in a separate environment
   *
   * <p>TT ID: d8188bb9-6b06-4133-afcd-f28db8ebb043
   *
   * <pre>
   *   Test steps:
   *   1. Copy ApkDebug project to temporary directory (do not import)
   *   2. Open the prebuilt APK file to create an APK profiling and debugging project.
   *   3. Open the native library editor and attach C and C++ source code.
   *   4. Open the DemoActivity smali file to attach the Java source code.
   *   Verify:
   *   1. C and C++ code directories are attached to the native library.
   *   2. Java source code files are added to the project tree.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/64476008
  public void createFromPreexistingApk() throws Exception {
    File projectRoot = guiTest.copyProjectBeforeOpening("ApkDebug");

    profileOrDebugApk(guiTest.welcomeFrame(), new File(projectRoot, "prebuilt/app-x86-debug.apk"));

    guiTest.waitForBackgroundTasks();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    File debugSymbols = new File(projectRoot, "prebuilt/libsanangeles.so");

    String packagedLibraryPath = "lib/x86/libsanangeles.so";
    Wait.seconds(5)
      .expecting("libsanangeles.so to be available")
      .until(() -> ideFrame.findFileByRelativePath(packagedLibraryPath, true) != null);
    LibraryEditorFixture libraryEditor = editor.open(packagedLibraryPath)
      .getLibrarySymbolsFixture()
      .addDebugSymbols(debugSymbols);

    guiTest.waitForBackgroundTasks();
    File cppSources = new File(projectRoot, "app/src/main/cpp");
    libraryEditor.getPathMappings()
      .mapRemotePathToLocalPath(cppSources);
    libraryEditor.applyChanges();
    guiTest.waitForBackgroundTasks();

    ideFrame.getProjectView()
      .selectAndroidPane();
    attachJavaSources(ideFrame, new File(projectRoot, "app/src/main/java"));
    waitForJavaFileToShow(editor);

    List<ProjectViewFixture.NodeFixture> srcNodes = getNativeLibChildren(ideFrame, "libsanangeles");
    int numCppFolders = countOccurrencesOfFolderNameIn(srcNodes, "cpp");
    int numIncludeFolders = countOccurrencesOfFolderNameIn(srcNodes, "include");

    Assert.assertEquals(1, numCppFolders);
    Assert.assertEquals(2, numIncludeFolders);
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
  @RunIn(TestGroup.QA_UNRELIABLE) // Bug: http://b/65172343
  public void debugLocallyBuiltApk() throws Exception {
    File projectRoot = buildApkLocally("ApkDebug");

    profileOrDebugApk(guiTest.welcomeFrame(), new File(projectRoot, "app/build/outputs/apk/debug/app-x86-debug.apk"));

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    EditorFixture editor = ideFrame.getEditor();

    File debugSymbols = new File(projectRoot, "app/build/intermediates/cmake/debug/obj/x86/libsanangeles.so");
    editor.open("lib/x86/libsanangeles.so")
      .getLibrarySymbolsFixture()
      .addDebugSymbols(debugSymbols);
    guiTest.waitForBackgroundTasks();

    // Add Java sources after adding native library debugging symbols due to b/62476714
    attachJavaSources(ideFrame, new File(projectRoot, "app/src/main/java"));
    waitForJavaFileToShow(editor);

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

    emulator.createDefaultAVD(ideFrame.invokeAvdManager());
    ideFrame.debugApp(debugConfigName)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

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

  @After
  public void removeApkProjectsGeneratedDuringTest() {
    // An ~/ApkProjects directory will show us a dialog in a subsequent
    // test run. Clean up after ourselves by deleting the directory here.

    // Close before deleting, because IDE will write to some files that will be deleted
    // Prevents FileNotFoundException from occurring
    guiTest.ideFrame().closeProject();
    removeApkProjectsDirectory();
  }

  private static void removeApkProjectsDirectory() {
    File homeDir = new File(SystemProperties.getUserHome());
    File apkProjects = new File(homeDir, "ApkProjects");
    try {
      FileUtil.ensureExists(apkProjects);
      FileUtil.delete(apkProjects);
    } catch (IOException ignored) {
      // do nothing! Nothing to delete!
    }
  }

  @NotNull
  private List<ProjectViewFixture.NodeFixture> filterSourceFolderChildren(
      @NotNull List<ProjectViewFixture.NodeFixture> nodeChildren) {
    List<ProjectViewFixture.NodeFixture> filteredChildren = ContainerUtil.newArrayList();
    for (ProjectViewFixture.NodeFixture child : nodeChildren) {
      if (child.isSourceFolder()) {
        filteredChildren.add(child);
      }
    }
    return filteredChildren;
  }

  @NotNull
  private IdeFrameFixture attachJavaSources(@NotNull IdeFrameFixture ideFrame, @NotNull File sourceDir) {
    String smaliFile = "smali/out/com/example/SanAngeles/DemoActivity.smali";

    Wait.seconds(5)
      .expecting("DemoActivity.smali file to be indexed and shown")
      .until(() -> ideFrame.findFileByRelativePath(smaliFile, false) != null);

    ideFrame.getEditor()
      .open(smaliFile)
      .awaitNotification(
        "Disassembled classes.dex file. To set up breakpoints for debugging, please attach Java source files.")
      .performActionWithoutWaitingForDisappearance("Attach Java Sources...");

    FileChooserDialogFixture.findDialog(guiTest.robot(), "Attach Sources")
      .select(VfsUtil.findFileByIoFile(sourceDir, true))
      .clickOk();
    return ideFrame;
  }

  private void waitForJavaFileToShow(@NotNull EditorFixture editor) {
    Wait.seconds(5)
      .expecting("DemoActivity.java file to open after attaching sources")
      .until(() -> "DemoActivity.java".equals(editor.getCurrentFileName()));
  }

  @NotNull
  private List<ProjectViewFixture.NodeFixture> getNativeLibChildren(@NotNull IdeFrameFixture ideFrame, @NotNull String libraryName) {
    ProjectViewFixture.NodeFixture libNode = ideFrame
      .getProjectView()
      .selectAndroidPane()
      .findNativeLibraryNodeFor(libraryName);
    return filterSourceFolderChildren(libNode.getChildren());
  }

  private int countOccurrencesOfFolderNameIn(@NotNull Iterable<ProjectViewFixture.NodeFixture> nodes, @NotNull String folderName) {
    int numFolders = 0;
    for (ProjectViewFixture.NodeFixture node : nodes) {
      if(folderName.equals(node.getSourceFolderName())) {
        numFolders++;
      }
    }
    return numFolders;
  }

  private void profileOrDebugApk(@NotNull WelcomeFrameFixture welcomeFrame, @NotNull File apk) {
    // Opening the APK profiling/debugging dialog can set the Modality to a state where
    // VfsUtil.findFileByIoFile blocks us indefinitely. Retrieve
    // VirtualFile before we open the dialog:
    VirtualFile apkFile = VfsUtil.findFileByIoFile(apk, true);
    welcomeFrame.profileDebugApk();

    // This step generates the ~/ApkProjects/app-x86-debug directory. This
    // directory will be removed as a part of our tests' cleanup methods.
    FileChooserDialogFixture.findDialog(guiTest.robot(), "Select APK File")
      .select(apkFile)
      .clickOk();

    guiTest.waitForBackgroundTasks();
  }

  @NotNull
  private File buildApkLocally(@NotNull String apkProjectToImport) throws IOException {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish(apkProjectToImport);

    ideFrame.waitAndInvokeMenuPath("Build", "Build APK(s)");
    guiTest.waitForBackgroundTasks();

    File projectRoot = ideFrame.getProjectPath();

    // We will have another window opened for the APK project. Close this window
    // so we don't have to manage two windows.
    ideFrame.closeProject();

    return projectRoot;
  }
}
