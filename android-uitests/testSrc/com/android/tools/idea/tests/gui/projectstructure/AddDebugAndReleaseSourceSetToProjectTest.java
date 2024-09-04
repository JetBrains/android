/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.projectstructure;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.NewJavaClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewFolderWizardFixture;
import com.android.tools.idea.tests.util.WizardUtils;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddDebugAndReleaseSourceSetToProjectTest {

  @Rule
  public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  private IdeFrameFixture ideFrame;

  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, "Empty Views Activity"); // Default projects are created with androidx
    // dependencies
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame = guiTest.ideFrame();
    ideFrame.clearNotificationsPresentOnIdeFrame();
  }

  /**
   * Verifies user can add debug and release source set to project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: b0e0d2bc-7c8a-40fe-979c-3a43d969088a
   * <p>
   *
   * <pre>
   *   Test Steps:
   *   Debug Source set
   *   1) Create a new project
   *   2) From Android View > Right Click on Java Folder > New > Folder > Java Folder
   *   3) n the Wizard select debug source set > Finish
   *   4) Switch to Project View
   *   5) Under src/debug/java > Create a Java Class including package structure (ex: com.example.test.ProjectTestClass)
   *   6) Switch to Android View (Verify 1)
   *   7) Try to access ProjectTestClass from MainActivity (Verify 2)
   *   Release source set
   *   8) Switch to release build variant
   *   9) From Android View > Right Click on Java Folder > New > Folder > Java Folder
   *   10) In the Wizard select release source set > Finish
   *   11) Switch to Project View
   *   12) Under src/debug/java > Create a Java Class including package structure (ex: com.example.test.ProjectReleaseClass)
   *   13) Switch to Android View (Verify 3)
   *   14) Try to access ProjectTestClass from MainActivity (Verify 2)
   *   15) Switch between (debug/release) variants from Build Variants Window (Verify 4)
   *   Verify:
   *   1) There should be a package name  with name com.example.test and ProjectTestClass  Java class
   *   2)  Import statement should import with shortcut (Alt+ Enter) & Symbol should resolve
   *   3)  There should be a package name  with name com.example.test and ProjectReleaseClass  Java class
   *   4) Only  selected variant package/class should be displayed in Android View
   * </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testDebugAndReleaseSourceSetToProject() throws Exception {
    final String DEBUG_IMPORT_CLASS_NAME = "com.google.myapplication.DebugTest.BuildVariantDebugClass";
    final String DEBUG_IMPORT_CLASS_STRING_USAGE = "BuildVariantDebugClass testingClassDebug = new BuildVariantDebugClass();";
    final String RELEASE_IMPORT_CLASS_NAME = "com.google.myapplication.ReleaseTest.BuildVariantReleaseClass";
    final String RELEASE_IMPORT_CLASS_STRING_USAGE = "BuildVariantReleaseClass testClassRelease = new BuildVariantReleaseClass();";

    EditorFixture editor = ideFrame.getEditor();

    // Clearing any notifications on the ideFrame
    ideFrame.clearNotificationsPresentOnIdeFrame();

    // Debug Source Set test (Steps 1-7)
    NewFolderWizardFixture newFolderCreationDebug = NewFolderWizardFixture.open(ideFrame);
    newFolderCreationDebug.selectResFolder("debug");
    newFolderCreationDebug.clickFinishAndWaitForSyncToComplete();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    createNewJavaClass(getProjectPane(),
                        ideFrame,
                       "debug",
                        "com.google.myapplication.DebugTest.BuildVariantDebugClass"
    );
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    getAndroidPane();

    String FILE_CONTENTS_BEFORE_CHANGES = editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
        .getCurrentFileContents();

    editor.moveBetween("super.onCreate(savedInstanceState);", "")
      .enterText("\n" + DEBUG_IMPORT_CLASS_STRING_USAGE);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editor.moveBetween("BuildVariantDebugC", "lass")
        .waitUntilErrorAnalysisFinishes();
    invokeAltEnter();

    assertThat(editor.getCurrentFileContents()).contains("import " + DEBUG_IMPORT_CLASS_NAME + ";");

    ideFrame.requestProjectSyncAndWaitForSyncToFinish();

    // Clearing the file to previous state
    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .replaceFileContents(FILE_CONTENTS_BEFORE_CHANGES);

    // Release source set test (Steps 8-15)
    ideFrame.getBuildVariantsWindow()
      .waitTillTableIsActivated()
      .selectVariantForModule("My_Application.app", "release");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.getBuildVariantsWindow()
        .hide(); // Hiding the tool-window to avoid change pane issues.

    ideFrame.requestProjectSyncAndWaitForSyncToFinish();
    GuiTests.refreshFiles();

    GuiTests.waitForProjectIndexingToFinish(ideFrame.getProject());

    NewFolderWizardFixture newFolderCreationRelease = NewFolderWizardFixture.open(ideFrame);
    newFolderCreationRelease.selectResFolder("release");
    newFolderCreationRelease.clickFinishAndWaitForSyncToComplete();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    createNewJavaClass(getProjectPane(),
                         ideFrame,
                       "release",
                         "com.google.myapplication.ReleaseTest.BuildVariantReleaseClass"
    );

    getAndroidPane();

    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .moveBetween("super.onCreate(savedInstanceState);", "")
      .enterText("\n" + RELEASE_IMPORT_CLASS_STRING_USAGE);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editor.moveBetween("BuildVariantReleaseC", "lass")
        .waitUntilErrorAnalysisFinishes();
    invokeAltEnter();
    assertThat(editor.getCurrentFileContents()).contains("import " + RELEASE_IMPORT_CLASS_NAME + ";");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Testing toggle between release and debug projects (Step 15)
    ideFrame.getBuildVariantsWindow()
      .waitTillTableIsActivated()
      .selectVariantForModule("My_Application.app", "debug (default)");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.getProjectView()
      .assertFilesExist("/app/src/debug/java/com/google/myapplication/DebugTest/BuildVariantDebugClass.java");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.getBuildVariantsWindow()
      .waitTillTableIsActivated()
      .selectVariantForModule("My_Application.app", "release");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.getProjectView()
      .assertFilesExist(
        "/app/src/release/java/com/google/myapplication/ReleaseTest/BuildVariantReleaseClass.java");
  }

  private ProjectViewFixture.PaneFixture getProjectPane() {
    return ideFrame
      .getProjectView()
      .selectProjectPane();
  }

  private ProjectViewFixture.PaneFixture getAndroidPane() {
    return ideFrame
      .getProjectView()
      .selectAndroidPane();
  }

  private void createNewJavaClass(@NotNull ProjectViewFixture.PaneFixture projectPane,
                                    @NotNull IdeFrameFixture ideFrameFixture,
                                    @NotNull String location,
                                    @NotNull String name) {
    Wait.seconds(30).expecting("Path should be found.").until(() -> {
      try {
        projectPane.clickPath("MyApplication", "app", "src", location, "java")
          .invokeMenuPath("File", "New", "Java Class");
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });

    NewJavaClassDialogFixture.find(ideFrameFixture)
      .enterName(name)
      .clickOk();

    String fileName = "Class.java";
    // The flakiness here is 1/1000. Increase the timeout from 10s to 15s to stabilize it.
    Wait.seconds(15).expecting(fileName + " file should be opened")
      .until(() -> ideFrameFixture.getEditor().getCurrentFileName().contains(fileName));
  }

  private void invokeAltEnter() {
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER, KeyEvent.ALT_MASK);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
}