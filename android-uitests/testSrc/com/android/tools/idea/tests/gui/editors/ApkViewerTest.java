/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editors;

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ApkViewerFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture.PaneFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SelectPathFixture;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class ApkViewerTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String APK_NAME = "app-debug.apk";

  /***
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: be75b1ab-005d-43f8-97c2-b84efded54ac
   * <pre>
   *   Verifies APK Viewer gets launched when analyzing apk
   *   Test Steps
   *   1. Import a project
   *   2. Build APK
   *   3. Analyze APK
   *   Verification
   *   1. Ensure APK entries appear for classes.dex, AndroidManifest.xml
   * </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void launchApkViewer() throws Exception {
    List<String> apkEntries = guiTest.importSimpleLocalApplication()
      .invokeMenuPath("Build", "Build APK(s)")
      .waitForBuildToFinish(BuildMode.ASSEMBLE)
      .openFromMenu(SelectPathFixture::find, "Build", "Analyze APK...")
      .clickOK()
      .getEditor()
      .getApkViewer(APK_NAME)
      .getApkEntries();
    assertThat(apkEntries).contains("AndroidManifest.xml");
    assertThat(apkEntries).contains("classes.dex");
  }

  /***
   * To verify that the file handle to apk is released by the APK analyzer after analyzing and
   * closing the apk.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 3a70500c-038c-4211-99c1-0d9579574cd1
   * <pre>
   *   Test Steps
   *   1. Import a project.
   *   2. Build APK.
   *   3. Double click an apk to open Apk Analyzer in AS project view.
   *   4. Click around (classes.dex, resources.asrc, etc).
   *   5. Close tab.
   *   6. Make some changes in source code, and re-build APK and verify the build is successful.
   * </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testFileHandleRelease() throws Exception {
    final String SIMPLE_APP = "SimpleLocalApplication";
    final String APP = "app";
    final String BUILD = "build";
    final String OUTPUTS = "outputs";
    final String APK = "apk";
    final String DEBUG = "debug";
    final String APK_FILE_PATH = String.format("%s/%s/%s/%s/%s/%s",
                                               APP, BUILD, OUTPUTS, APK, DEBUG, APK_NAME);

    IdeFrameFixture ideFrame = guiTest.importSimpleLocalApplication();

    ProjectViewFixture projectView = ideFrame.invokeMenuPath("Build", "Build APK(s)")
      .waitForBuildToFinish(BuildMode.ASSEMBLE)
      .getProjectView();

    PaneFixture paneFixture = projectView.selectPane(ProjectViewPane.ID, "Project");
    paneFixture.expand();
    paneFixture.clickPath(SIMPLE_APP, APP, BUILD, OUTPUTS, APK, DEBUG);

    EditorFixture editor = ideFrame.getEditor();
    editor.open(APK_FILE_PATH);
    ApkViewerFixture apkViewer = editor.getApkViewer(APK_NAME);
    apkViewer.clickApkEntry("resources.arsc");
    apkViewer.clickApkEntry("AndroidManifest.xml");
    editor.close();

    // Open source code and make some changes, then trigger a build.
    // Build should be successful.
    ideFrame.getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .moveBetween("setContentView(R.layout.activity_my);", "")
      .enterText("\nSystem.out.println(\"Hello.\");")
      .close();

    ideFrame.invokeMenuPath("Build", "Build APK(s)")
      .waitForBuildToFinish(BuildMode.ASSEMBLE);
  }
}
