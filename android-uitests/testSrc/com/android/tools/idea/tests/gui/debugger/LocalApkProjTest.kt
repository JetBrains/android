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
package com.android.tools.idea.tests.gui.debugger

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.matcher.DialogMatcher
import org.fest.swing.core.matcher.JButtonMatcher
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.finder.WindowFinder
import org.fest.swing.timing.Wait
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class LocalApkProjTest {
  /*
   * This is a rather long test. A lot of waiting is done in the background waiting
   * for file refreshes. This test needs the huge timeout, even though it does not use
   * the emulator.
   */
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(10, TimeUnit.MINUTES)

  @Before
  fun removeExistingApkProjects() {
    // An ~/ApkProjects directory will show us a dialog in the middle of the test
    // to overwrite the directory. Delete the directory now so it won't trip the test.
    CreateAPKProjectTestUtil.removeApkProjectsDirectory()
  }

  /**
   * Verifies source code directories are set for locally built APKs.
   *
   * TT ID: 47e054af-29c2-4bfa-8410-e2f826452e78
   *
   * Test steps:
   * 1. Import ApkDebug.
   * 2. Build APK
   * 3. Close ApkDebug project window.
   * 4. Create APK debugging project with the locally built APK
   * 5. Open a .smali file to attach Java sources
   * 6. Open the native library to view the native library editor window.
   * 8. Attach debug symbols for the library.
   *
   * Verify:
   * 1. C and C++ code directories are attached to the native library
   * 2. Java source code files are added to the project tree.
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  fun createProjectFromLocallyBuiltApk() {
    val projectRoot = buildApkLocally("ApkDebug")

    profileOrDebugApk(guiTest.welcomeFrame(), File(projectRoot, "app/build/outputs/apk/debug/app-x86-debug.apk"))

    // Handle the APK Import dialog pop up if running in the IDE etc.
    val downloadDialog = try {
      WindowFinder
        .findDialog(DialogMatcher.withTitle("APK Import"))
        .withTimeout(1, TimeUnit.SECONDS)
        .using(guiTest.robot())
    }
    catch (e: WaitTimedOutError) {
      null
    }
    if (downloadDialog != null) {
      val useExistFolder = downloadDialog.button(JButtonMatcher.withText("Use existing folder"))
      Wait.seconds(120).expecting("Android source to be installed").until { useExistFolder.isEnabled }
      useExistFolder.click()
    }

    val ideFrame = guiTest.ideFrame()
    val editor = ideFrame.editor
    attachJavaSources(ideFrame, File(projectRoot, "app/src/main/java"))

    // need to wait since the editor doesn't open the java file immediately
    waitForJavaFileToShow(editor)

    val debugSymbols = File(projectRoot, "app/build/intermediates/merged_native_libs/debug/out/lib/x86/libsanangeles.so")
    editor.open("lib/x86/libsanangeles.so")
      .librarySymbolsFixture
      .addDebugSymbols(debugSymbols)
    guiTest.waitForBackgroundTasks()

    val srcNodes = getLibChildren(ideFrame, "libsanangeles")

    Assert.assertEquals(2, countOccurrencesOfSourceFolders(srcNodes).toLong())
  }

  private fun buildApkLocally(apkProjectToImport: String): File {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish(apkProjectToImport, Wait.seconds(120))

    guiTest.waitForBackgroundTasks()

    ideFrame.invokeAndWaitForBuildAction("Build", "Build Bundle(s) / APK(s)", "Build APK(s)")

    guiTest.waitForBackgroundTasks()

    val projectRoot = ideFrame.projectPath
    // We will have another window opened for the APK project. Close this window
    // so we don't have to manage two windows.
    ideFrame.closeProject()

    return projectRoot
  }

  private fun profileOrDebugApk(welcomeFrame: WelcomeFrameFixture, apk: File) {
    // Opening the APK profiling/debugging dialog can set the Modality to a state where
    // VfsUtil.findFileByIoFile blocks us indefinitely. Retrieve
    // VirtualFile before we open the dialog:
    val apkFile = VfsUtil.findFileByIoFile(apk, true) ?: throw IllegalStateException("${apk.absolutePath} does not exist")

    // NOTE: This step generates the ~/ApkProjects/app-x86-debug directory.
    welcomeFrame.profileOrDebugApk(apk)
      .select(apkFile)
      .clickOkAndWaitToClose()
  }

  private fun attachJavaSources(ideFrame: IdeFrameFixture, sourceDir: File): IdeFrameFixture {
    val smaliFile = "smali/out/com/example/SanAngeles/DemoActivity.smali"
    val sourceDirVirtualFile = VfsUtil.findFileByIoFile(sourceDir, true) ?: throw IllegalArgumentException("Nonexistent $sourceDir")

    ideFrame.editor
      .open(smaliFile)
      .awaitNotification(
        "Disassembled classes.dex file. To set up breakpoints for debugging, please attach Kotlin/Java source files.")
      .performActionWithoutWaitingForDisappearance("Attach Kotlin/Java Sources...")

    GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(120))
    guiTest.robot().waitForIdle()
    // b/70731570 investigates the long amount of
    // time the IDE takes to show the file tree in the file picker. Unfortunately,
    // the amount of time the IDE takes is much longer than the amount of time the
    // findDialog() method waits for the file tree to appear. findDialog() is likely
    // to time out. This catch block is here to let us try waiting again until the
    // IDE finally shows the file tree.
    // TODO: remove the following ugly Wait once b/70731570 is fixed.
    Wait.seconds(300)
      .expecting("file chooser dialog to show the file tree")
      .until {
        try {
          FileChooserDialogFixture.findDialog(guiTest.robot(), "Attach Sources")
          true
        }
        catch (timeout: WaitTimedOutError) {
          false
        }
      }
    FileChooserDialogFixture.findDialog(guiTest.robot(), "Attach Sources")
      .select(sourceDirVirtualFile)
      .clickOk()
    return ideFrame
  }

  private fun waitForJavaFileToShow(editor: EditorFixture) {
    Wait.seconds(5)
      .expecting("DemoActivity.java file to open after attaching sources")
      .until { "DemoActivity.java" == editor.currentFileName }
  }

  private fun getLibChildren(ideFrame: IdeFrameFixture, libraryName: String): List<ProjectViewFixture.NodeFixture> {
    val libNode = ideFrame
      .projectView
      .selectAndroidPane()
      .findNativeLibraryNodeFor(libraryName)
    return libNode.children
  }

  private fun countOccurrencesOfSourceFolders(nodes: Iterable<ProjectViewFixture.NodeFixture>): Int {
    val sourceFolders = ArrayList<ProjectViewFixture.NodeFixture>()
    nodes.forEach { fixture ->
      if (fixture.isSourceFolder) {
        sourceFolders.add(fixture)
      }
    }
    return sourceFolders.size
  }
}