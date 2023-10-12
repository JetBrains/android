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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ApkViewerFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(GuiTestRemoteRunner.class)
public class ApkViewerTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private static final String APK_NAME = "app-debug.apk";
  private final String APK_FILE_PATH = "app/build/outputs/apk/debug/app-debug.apk";

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
  @Test
  public void testFileHandleRelease() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.invokeAndWaitForBuildAction(Wait.seconds(300), "Build", "Build Bundle(s) / APK(s)", "Build APK(s)");
    GuiTests.waitForProjectIndexingToFinish(ideFrame.getProject());
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.robot()
      .waitForIdle();
    ideFrame.requestFocusIfLost();

    EditorFixture editor = ideFrame.getEditor();
    guiTest.waitForBackgroundTasks();

    ideFrame.getProjectView()
      .selectProjectPane()
      .hasPath(APK_FILE_PATH);

    editor.open(APK_FILE_PATH);
    ApkViewerFixture apkViewer = editor.getApkViewer(APK_NAME);
    apkViewer.clickApkEntry("resources.arsc");
    apkViewer.clickApkEntry("AndroidManifest.xml");
    apkViewer.clickApkEntry("classes.dex");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.close();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Open source code and make some changes, then trigger a build.
    // Build should be successful.
    editor
      .open("app/src/main/java/google/simpleapplication/MyActivity.java");

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    editor.moveBetween("super.onCreate(savedInstanceState);", "")
      .enterText("\n" + "System.out.println(\"Hello.\");" + "\n");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.invokeAndWaitForBuildAction(Wait.seconds(300), "Build", "Build Bundle(s) / APK(s)", "Build APK(s)");
    GuiTests.waitForProjectIndexingToFinish(ideFrame.getProject());
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
}
