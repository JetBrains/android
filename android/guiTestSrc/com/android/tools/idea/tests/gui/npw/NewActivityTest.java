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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureBasicActivityStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureBasicActivityStepFixture.ActivityTextField;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
public class NewActivityTest {
  private static final String PROVIDED_ACTIVITY = "app/src/main/java/google/simpleapplication/MyActivity.java";
  private static final String PROVIDED_MANIFEST = "app/src/main/AndroidManifest.xml";
  private static final String DEFAULT_ACTIVITY_NAME = "MainActivity";
  private static final String DEFAULT_LAYOUT_NAME = "activity_main";
  private static final String DEFAULT_ACTIVITY_TITLE = "MainActivity";


  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private EditorFixture myEditor;
  private NewActivityWizardFixture myDialog;
  private ConfigureBasicActivityStepFixture myConfigActivity;

  @Before
  public void setUp() throws IOException {
    guiTest.importSimpleApplication();
    myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(PROVIDED_ACTIVITY);

    guiTest.ideFrame().getProjectView().assertFilesExist(
      "settings.gradle",
      "app",
      PROVIDED_ACTIVITY,
      PROVIDED_MANIFEST
    );

    guiTest.ideFrame().invokeMenuPath("File", "New", "Activity", "Basic Activity");
    myDialog = NewActivityWizardFixture.find(guiTest.robot());

    myConfigActivity = myDialog.getConfigureActivityStep();
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, DEFAULT_ACTIVITY_TITLE);
  }

  @Test
  public void createDefaultActivity() {
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    guiTest.ideFrame().getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/MainActivity.java",
      "app/src/main/res/layout/activity_main.xml"
    );

    myEditor.open(PROVIDED_MANIFEST);

    String text = myEditor.getCurrentFileContents();
    assertEquals(StringUtil.getOccurrenceCount(text, "android:name=\".MainActivity\""), 1);
    assertEquals(StringUtil.getOccurrenceCount(text, "@string/title_activity_main"), 1);
    assertEquals(StringUtil.getOccurrenceCount(text, "android.intent.category.LAUNCHER"), 1);
  }

  @Test
  public void createLauncherActivity() {
    myConfigActivity.selectLauncherActivity();
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    myEditor.open(PROVIDED_MANIFEST);

    String text = myEditor.getCurrentFileContents();
    assertEquals(StringUtil.getOccurrenceCount(text, "android.intent.category.LAUNCHER"), 2);
  }

  @Test
  public void createActivityWithFragment() {
    myConfigActivity.selectUseFragment();
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    guiTest.ideFrame().getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/MainActivity.java",
      "app/src/main/res/layout/fragment_main.xml"
    );
  }

  @Test
  public void createActivityWithHierarchicalParent() throws Exception{
    myConfigActivity.enterTextFieldValue(ActivityTextField.HIERARCHICAL_PARENT, "google.simpleapplication.MyActivity");
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    String text = myEditor.open(PROVIDED_MANIFEST).getCurrentFileContents();
    assertEquals(StringUtil.getOccurrenceCount(text, "android:name=\".MainActivity\""), 1);
    assertEquals(StringUtil.getOccurrenceCount(text, "android:parentActivityName=\".MyActivity\">"), 1);
  }

  @Test
  public void createActivityWithInvalidHierarchicalParent() throws Exception {
    myConfigActivity.enterTextFieldValue(ActivityTextField.HIERARCHICAL_PARENT, "google.simpleapplication.MyActivityWrong");
    assertThat(myConfigActivity.getValidationText()).isEqualTo("Hierarchical Parent must already exist");
    myDialog.clickCancel();
  }

  @Test
  public void createActivityWithNonDefaultPackage() throws Exception{
    myConfigActivity.enterTextFieldValue(ActivityTextField.PACKAGE_NAME, "google.test2");
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    String text = myEditor.open("app/src/main/java/google/test2/MainActivity.java").getCurrentFileContents();
    assertThat(text).startsWith("package google.test2;");
  }

  @Test
  public void changeActivityName() {
    // Changing "Activity Name" causes "Title" and "Layout Name" to change
    myConfigActivity.enterTextFieldValue(ActivityTextField.NAME, "MainActivityTest");
    assertTextFieldValues("MainActivityTest", "activity_main_activity_test", "MainActivityTest");

    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    guiTest.ideFrame().getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/MainActivityTest.java",
      "app/src/main/res/layout/activity_main_activity_test.xml"
    );
  }

  @Test
  public void changeActivityNameUndo() throws Exception {
    // Changing "Activity Name" causes "Title" and "Layout Name" to change
    myConfigActivity.enterTextFieldValue(ActivityTextField.NAME, "MainActivityTest");
    assertTextFieldValues("MainActivityTest", "activity_main_activity_test", "MainActivityTest");

    // Undo "Activity Name" (Right-click and choose "Restore default value") should revert all changes
    myConfigActivity.undoTextFieldValue(ActivityTextField.NAME);
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, DEFAULT_ACTIVITY_TITLE);

    myDialog.clickCancel();
  }

  @Test
  public void changeLayoutNameUndo() throws Exception {
    // Changing "Layout Name" causes "Activity Name" and "Title" to change
    myConfigActivity.enterTextFieldValue(ActivityTextField.LAYOUT, "activity_main_test1");
    assertTextFieldValues("MainTest1Activity", "activity_main_test1", "MainTest1Activity");

    // Undo "Layout Name", should revert all changes
    myConfigActivity.undoTextFieldValue(ActivityTextField.LAYOUT);
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, DEFAULT_ACTIVITY_TITLE);

    myDialog.clickCancel();
  }

  @Test
  public void changeTitleNameUndo() throws Exception {
    // Changing "Title" does not change "Activity Name" or "Layout Name"
    myConfigActivity.enterTextFieldValue(ActivityTextField.TITLE, "Main Activity Test3");
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, "Main Activity Test3");

    // Undo "Title"
    myConfigActivity.undoTextFieldValue(ActivityTextField.TITLE);
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, DEFAULT_ACTIVITY_TITLE);

    myDialog.clickCancel();
  }

  @Test
  public void changeActivityThenLayoutNameUndo() throws Exception {
    // Changing "Activity Name" causes "Title" and "Layout Name" to change, after that
    // "Activity Name" should be "locked", changing LAYOUT should not update any other field
    myConfigActivity.enterTextFieldValue(ActivityTextField.NAME, "MainActivityTest1");
    myConfigActivity.enterTextFieldValue(ActivityTextField.LAYOUT, "main_activity2");
    assertTextFieldValues("MainActivityTest1", "main_activity2", "MainActivityTest1");

    // Undo "Layout Name" should only undo that field
    myConfigActivity.undoTextFieldValue(ActivityTextField.LAYOUT);
    assertTextFieldValues("MainActivityTest1", "activity_main_activity_test1", "MainActivityTest1");

    // Undo "Activity Name" should revert all changes
    myConfigActivity.undoTextFieldValue(ActivityTextField.NAME);
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, DEFAULT_ACTIVITY_TITLE);

    myDialog.clickCancel();
  }

  @Test
  public void changeActivityThenTitleNameUndo() throws Exception {
    // Changing "Activity Name", then "Title", then "Activity Name" again. "Title" should not update since it's been manually modified.
    myConfigActivity.enterTextFieldValue(ActivityTextField.NAME, "MainActivityTest1");
    myConfigActivity.enterTextFieldValue(ActivityTextField.TITLE, "Main Activity Test3");
    myConfigActivity.enterTextFieldValue(ActivityTextField.NAME, "MainActivityTest123");
    assertTextFieldValues("MainActivityTest123", "activity_main_activity_test123", "Main Activity Test3");

    // Undo "Activity Name", should update "Activity Name" and LAYOUT, but not the title
    myConfigActivity.undoTextFieldValue(ActivityTextField.NAME);
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, "Main Activity Test3");

    // And undo of "Title" should bring everything to its default values
    myConfigActivity.undoTextFieldValue(ActivityTextField.TITLE);
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, DEFAULT_ACTIVITY_TITLE);

    myDialog.clickCancel();
  }

  private void assertTextFieldValues(@NotNull String activityName, @NotNull String layoutName, @NotNull String title) {
    assertThat(myConfigActivity.getTextFieldValue(ActivityTextField.NAME)).isEqualTo(activityName);
    assertThat(myConfigActivity.getTextFieldValue(ActivityTextField.LAYOUT)).isEqualTo(layoutName);
    assertThat(myConfigActivity.getTextFieldValue(ActivityTextField.TITLE)).isEqualTo(title);
  }
}
