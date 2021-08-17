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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class CreateAPKProjectTestUtil {

  public static void profileOrDebugApk(@NotNull GuiTestRule guiTest,
                                       @NotNull File apk) {
    // Opening the APK profiling/debugging dialog can set the Modality to a state where
    // VfsUtil.findFileByIoFile blocks us indefinitely. Retrieve
    // VirtualFile before we open the dialog:
    VirtualFile apkFile = VfsUtil.findFileByIoFile(apk, true);

    // This step generates the ~/ApkProjects/app-x86-debug directory. This
    // directory will be removed as a part of our tests' cleanup methods.
    guiTest.welcomeFrame().profileOrDebugApk(apk)
      .select(apkFile)
      .clickOkAndWaitToClose();

    guiTest.waitForBackgroundTasks();
  }

  @NotNull
  public static IdeFrameFixture attachJavaSources(@NotNull IdeFrameFixture ideFrame, @NotNull File sourceDir) {
    String smaliFile = "smali/out/com/example/SanAngeles/DemoActivity.smali";

    Wait.seconds(5)
      .expecting("DemoActivity.smali file to be indexed and shown")
      .until(() -> ideFrame.findFileByRelativePath(smaliFile) != null);

    // The file chooser is quite slow and we don't have a good way to find when loading finished (there used to be
    // a loading spinner, but was removed from the platform). To make sure we don't have to wait, we pre-inject the path.
    FileChooserUtil.setLastOpenedFile(ideFrame.getProject(), sourceDir.toPath());

    ideFrame.getEditor()
      .open(smaliFile)
      .awaitNotification(
        "Disassembled classes.dex file. To set up breakpoints for debugging, please attach Kotlin/Java source files.")
      .performActionWithoutWaitingForDisappearance("Attach Kotlin/Java Sources...");

    FileChooserDialogFixture.findDialog(ideFrame.robot(), "Attach Sources")
      .select(VfsUtil.findFileByIoFile(sourceDir, true))
      .clickOk();
    return ideFrame;
  }

  protected static void removeApkProjectsDirectory() {
    File homeDir = new File(SystemProperties.getUserHome());
    File apkProjects = new File(homeDir, "ApkProjects");
    try {
      FileUtil.ensureExists(apkProjects);
      FileUtil.delete(apkProjects);
    } catch (IOException ignored) {
      // do nothing! Nothing to delete!
    }
  }

  public static void waitForJavaFileToShow(@NotNull EditorFixture editor) {
    Wait.seconds(5)
      .expecting("DemoActivity.java file to open after attaching sources")
      .until(() -> "DemoActivity.java".equals(editor.getCurrentFileName()));
  }

  @NotNull
  public static List<ProjectViewFixture.NodeFixture> getLibChildren(@NotNull IdeFrameFixture ideFrame, @NotNull String libraryName) {
    ProjectViewFixture.NodeFixture libNode = ideFrame
      .getProjectView()
      .selectAndroidPane()
      .findNativeLibraryNodeFor(libraryName);
    return libNode.getChildren();
  }

  public static int countOccurrencesOfSourceFolders(@NotNull Iterable<ProjectViewFixture.NodeFixture> nodes) {
    Collection<ProjectViewFixture.NodeFixture> sourceFolders = new ArrayList<>();
    nodes.forEach(fixture -> {
      if (fixture.isSourceFolder()) {
        sourceFolders.add(fixture);
      }
    });
    return sourceFolders.size();
  }
}
