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

import com.android.tools.idea.tests.gui.framework.fixture.NewJavaClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewFolderWizardFixture;
import com.android.tools.idea.tests.util.WizardUtils;


import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import org.fest.swing.core.MouseButton;
import org.fest.swing.timing.Wait;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddDebugAndReleaseSourceSetToProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  protected static final String BASIC_ACTIVITY_TEMPLATE = "Basic Activity";
  protected static final String DEBUG_IMPORT_CLASS_NAME = "android.com.myapplication.debugimportclass.testdebugclass";
  protected static final String DEBUG_IMPORT_CLASS_STRING_USAGE = "testdebugclass testingString = new testdebugclass();";
  protected static final String RELEASE_IMPORT_CLASS_NAME = "android.com.myapplication.releaseimportclass.testreleaseclass";
  protected static final String RELEASE_IMPORT_CLASS_STRING_USAGE = "testreleaseclass testingString = new testreleaseclass();";
  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, BASIC_ACTIVITY_TEMPLATE); // Default projects are created with androidx dependencies
    guiTest.robot().waitForIdle();
  }

  /**
   * Verifies user can add debug and release source set to project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: b0e0d2bc-7c8a-40fe-979c-3a43d969088a
   * <p>
   *   <pre>
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
   *   </pre>
   * <p>
   */


  private void WaitForBackgroundTasksToBeCompleted(){
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();
  }

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testDebugAndReleaseSourceSetToProject() throws Exception {

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    // Testing Debug Source Set (From Steps 1 to 7)
    guiTest.ideFrame()
      .getBuildVariantsWindow().selectVariantForModule("My_Application.app", "debug");

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "java")
      .openFromMenu(NewFolderWizardFixture::find, "New", "Folder", "Java Folder")
      .selectResFolder("debug")
      .clickFinishAndWaitForSyncToComplete()
      .getProjectView()
      .selectProjectPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "MyApplication", "app", "src", "debug", "java")
      .openFromMenu(NewJavaClassDialogFixture::find, "New", "Java Class")
      .enterName(DEBUG_IMPORT_CLASS_NAME).clickOk();

    ideFrame.getProjectView()
      .selectAndroidPane();

    WaitForBackgroundTasksToBeCompleted();

    ideFrame.find(guiTest.robot())
      .requestFocusIfLost();

    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .moveBetween("super.onCreate(savedInstanceState);", "")
      .enterText("\n" + DEBUG_IMPORT_CLASS_STRING_USAGE);

    WaitForBackgroundTasksToBeCompleted();

    //editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 5);

    editor.moveBetween("testdebugc", "lass");

    WaitForBackgroundTasksToBeCompleted();

    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER, KeyEvent.ALT_MASK);

    WaitForBackgroundTasksToBeCompleted();

    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);

    WaitForBackgroundTasksToBeCompleted();

    assertThat(editor.getCurrentFileContents()).contains("import " + DEBUG_IMPORT_CLASS_NAME + ";");


    // Testing Release Source Set (From Steps 8 to 14)

    guiTest.ideFrame()
      .getBuildVariantsWindow().selectVariantForModule("My_Application.app", "release");

    WaitForBackgroundTasksToBeCompleted();

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "java")
      .openFromMenu(NewFolderWizardFixture::find, "New", "Folder", "Java Folder")
      .selectResFolder("release")
      .clickFinishAndWaitForSyncToComplete()
      .getProjectView()
      .selectProjectPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "MyApplication", "app", "src", "release", "java")
      .openFromMenu(NewJavaClassDialogFixture::find, "New", "Java Class")
      .enterName(RELEASE_IMPORT_CLASS_NAME).clickOk();

    ideFrame.getProjectView()
      .selectAndroidPane();

    WaitForBackgroundTasksToBeCompleted();

    ideFrame.find(guiTest.robot())
      .requestFocusIfLost();

    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .moveBetween("super.onCreate(savedInstanceState);", "")
      .enterText("\n" + RELEASE_IMPORT_CLASS_STRING_USAGE);

    WaitForBackgroundTasksToBeCompleted();

    editor.moveBetween("testreleasec", "lass");

    WaitForBackgroundTasksToBeCompleted();

    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER, KeyEvent.ALT_MASK);

    WaitForBackgroundTasksToBeCompleted();

    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);

    WaitForBackgroundTasksToBeCompleted();

    assertThat(editor.getCurrentFileContents()).contains("import " + RELEASE_IMPORT_CLASS_NAME + ";");

    // Testing toggle between release and debug projects (Step 15)

    WaitForBackgroundTasksToBeCompleted();

    guiTest.ideFrame()
      .getBuildVariantsWindow().selectVariantForModule("My_Application.app", "debug");

    guiTest.ideFrame()
      .getProjectView()
      .assertFilesExist("/app/src/release/java/com/google/myapplication/releaseimportclass/testreleaseclass.java");

    WaitForBackgroundTasksToBeCompleted();

    guiTest.ideFrame()
      .getBuildVariantsWindow().selectVariantForModule("My_Application.app", "release");

    guiTest.ideFrame()
      .getProjectView()
      .assertFilesExist("/app/src/debug/java/com/google/myapplication/debugimportclass/testdebugclass.java");
  }
}