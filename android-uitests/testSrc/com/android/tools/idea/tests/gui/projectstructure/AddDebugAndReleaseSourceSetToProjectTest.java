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
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewFolderWizardFixture;
import com.android.tools.idea.tests.util.WizardUtils;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddDebugAndReleaseSourceSetToProjectTest {

  @Rule
  public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Views Activity";
  protected static final String DEBUG_IMPORT_CLASS_NAME = "com.google.myapplication.DebugTest.BuildVariantDebugClass";
  protected static final String DEBUG_IMPORT_CLASS_STRING_USAGE = "BuildVariantDebugClass testingClassDebug = new BuildVariantDebugClass();";
  protected static final String RELEASE_IMPORT_CLASS_NAME = "com.google.myapplication.ReleaseTest.BuildVariantReleaseClass";
  protected static final String RELEASE_IMPORT_CLASS_STRING_USAGE = "BuildVariantReleaseClass testClassRelease = new BuildVariantReleaseClass();";

  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE); // Default projects are created with androidx
    // dependencies
    guiTest.waitForAllBackgroundTasksToBeCompleted();
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

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    // Clearing any notifications on the ideframe
    ideFrame.clearNotificationsPresentOnIdeFrame();

    // Debug Source Set test (Steps 1-7)
    NewFolderWizardFixture newFolderCreationDebug = NewFolderWizardFixture.open(ideFrame);
    newFolderCreationDebug.selectResFolder("debug");
    newFolderCreationDebug.clickFinishAndWaitForSyncToComplete();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath("MyApplication", "app", "src", "debug", "java");
    ideFrame.invokeMenuPath("File", "New", "Java Class");
    NewJavaClassDialogFixture.find(ideFrame)
      .enterName(DEBUG_IMPORT_CLASS_NAME)
      .clickOk();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    ideFrame.getProjectView()
      .selectAndroidPane();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .moveBetween("super.onCreate(savedInstanceState);", "")
      .enterText("\n" + DEBUG_IMPORT_CLASS_STRING_USAGE);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editor.moveBetween("BuildVariantDebugC", "lass");
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();
    TimeUnit.SECONDS.sleep(5);
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER, KeyEvent.ALT_MASK);
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();
    List<String> options = editor.moreActionsOptions();

    assertEquals("Import class", options.get(0));
    assertEquals("Create class 'BuildVariantDebugClass'", options.get(1));
    assertEquals("Create interface 'BuildVariantDebugClass'", options.get(2));
    assertEquals("Create enum 'BuildVariantDebugClass'", options.get(3));
    assertEquals("Create inner class 'BuildVariantDebugClass'", options.get(4));
    assertEquals("Create type parameter 'BuildVariantDebugClass'", options.get(5));

    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editor.getCurrentFileContents()).contains("import " + DEBUG_IMPORT_CLASS_NAME + ";");

    ideFrame.requestProjectSyncAndWaitForSyncToFinish();

    // Release source set test (Steps 8-15)

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    ideFrame.getBuildVariantsWindow()
      .selectVariantForModule("My_Application.app", "release");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.requestProjectSyncAndWaitForSyncToFinish();
    GuiTests.refreshFiles();

    GuiTests.waitForProjectIndexingToFinish(ideFrame.getProject());

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    NewFolderWizardFixture newFolderCreationRelease = NewFolderWizardFixture.open(ideFrame);
    newFolderCreationRelease.selectResFolder("release");
    newFolderCreationRelease.clickFinishAndWaitForSyncToComplete();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath("MyApplication", "app", "src", "release", "java");
    ideFrame.invokeMenuPath("File", "New", "Java Class");
    NewJavaClassDialogFixture.find(ideFrame)
      .enterName(RELEASE_IMPORT_CLASS_NAME)
      .clickOk();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    ideFrame.getProjectView()
      .selectAndroidPane();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .moveBetween("super.onCreate(savedInstanceState);", "")
      .enterText("\n" + RELEASE_IMPORT_CLASS_STRING_USAGE);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editor.moveBetween("BuildVariantReleaseC", "lass");
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();
    TimeUnit.SECONDS.sleep(5);
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER, KeyEvent.ALT_MASK);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editor.getCurrentFileContents()).contains("import " + RELEASE_IMPORT_CLASS_NAME + ";");

    // Testing toggle between release and debug projects (Step 15)
    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    ideFrame.getBuildVariantsWindow()
      .selectVariantForModule("My_Application.app", "debug");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    ideFrame.getProjectView()
      .assertFilesExist("/app/src/debug/java/com/google/myapplication/DebugTest/BuildVariantDebugClass.java");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    ideFrame.getBuildVariantsWindow()
      .selectVariantForModule("My_Application.app", "release");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.find(guiTest.robot()).requestFocusIfLost();
    ideFrame.getProjectView()
      .assertFilesExist(
        "/app/src/release/java/com/google/myapplication/ReleaseTest/BuildVariantReleaseClass.java");
  }
}