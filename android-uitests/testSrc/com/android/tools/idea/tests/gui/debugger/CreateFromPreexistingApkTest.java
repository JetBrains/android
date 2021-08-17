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
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.LibraryEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.intellij.openapi.project.DumbService;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateFromPreexistingApkTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @Before
  public void removeExistingApkProjects() {
    // An ~/ApkProjects directory will show us a dialog in the middle of the test
    // to overwrite the directory. Delete the directory now so it won't trip the test.
    CreateAPKProjectTestUtil.removeApkProjectsDirectory();
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
  @RunIn(TestGroup.FAST_BAZEL)
  public void createFromPreexistingApk() throws Exception {
    File projectRoot = guiTest.copyProjectBeforeOpening("ApkDebug");

    CreateAPKProjectTestUtil.profileOrDebugApk(guiTest, new File(projectRoot, "prebuilt/app-x86-debug.apk"));

    guiTest.waitForBackgroundTasks();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    File debugSymbols = new File(projectRoot, "prebuilt/libsanangeles.so");

    String packagedLibraryPath = "lib/x86/libsanangeles.so";
    Wait.seconds(5)
      .expecting("libsanangeles.so to be available")
      .until(() -> ideFrame.findFileByRelativePath(packagedLibraryPath) != null && !DumbService.isDumb(ideFrame.getProject()));
    LibraryEditorFixture libraryEditor = editor.open(packagedLibraryPath)
      .getLibrarySymbolsFixture()
      .addDebugSymbols(debugSymbols);

    guiTest.waitForBackgroundTasks();
    File cppSources = new File(projectRoot, "app/src/main/cpp");
    libraryEditor.getPathMappings()
      .mapOriginalToLocalPath("cpp", cppSources);
    libraryEditor.applyChanges();
    guiTest.waitForBackgroundTasks();

    ideFrame.getProjectView()
      .selectAndroidPane();
    CreateAPKProjectTestUtil.attachJavaSources(ideFrame, new File(projectRoot, "app/src/main/java"));

    // Verifications:
    CreateAPKProjectTestUtil.waitForJavaFileToShow(editor);

    List<ProjectViewFixture.NodeFixture> srcNodes = CreateAPKProjectTestUtil.getLibChildren(ideFrame, "libsanangeles");

    Assert.assertEquals(2, CreateAPKProjectTestUtil.countOccurrencesOfSourceFolders(srcNodes));
  }

}
