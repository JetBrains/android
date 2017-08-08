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

import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureBasicActivityStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureBasicActivityStepFixture.ActivityTextField;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.util.PropertiesComponent;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.text.StringUtil.getOccurrenceCount;
import static org.junit.Assert.assertEquals;

@RunIn(TestGroup.PROJECT_WIZARD)
@RunWith(GuiTestRunner.class)
public class NewActivityTest {
  private static final String PROVIDED_ACTIVITY = "app/src/main/java/google/simpleapplication/MyActivity.java";
  private static final String PROVIDED_MANIFEST = "app/src/main/AndroidManifest.xml";
  private static final String APP_BUILD_GRADLE = "app/build.gradle";
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
    guiTest.ideFrame().getProjectView().selectProjectPane();
    myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(PROVIDED_ACTIVITY);

    guiTest.ideFrame().getProjectView().assertFilesExist(
      "settings.gradle",
      "app",
      PROVIDED_ACTIVITY,
      PROVIDED_MANIFEST
    );

    invokeNewActivityMenu();
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, DEFAULT_ACTIVITY_TITLE);
    assertThat(getSavedKotlinSupport()).isFalse();
    assertThat(getSavedRenderSourceLanguage()).isEqualTo(Language.JAVA);
  }

  @After
  public void tearDown() {
    setSavedKotlinSupport(false);
    setSavedRenderSourceLanguage(Language.JAVA);
  }

  @RunIn(TestGroup.QA)
  @Test
  public void createDefaultActivity() {
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    guiTest.ideFrame().getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/MainActivity.java",
      "app/src/main/res/layout/activity_main.xml"
    );

    String manifesText = myEditor.open(PROVIDED_MANIFEST).getCurrentFileContents();
    assertEquals(getOccurrenceCount(manifesText, "android:name=\".MainActivity\""), 1);
    assertEquals(getOccurrenceCount(manifesText, "@string/title_activity_main"), 1);
    assertEquals(getOccurrenceCount(manifesText, "android.intent.category.LAUNCHER"), 1);

    String gradleText = myEditor.open(APP_BUILD_GRADLE).getCurrentFileContents();
    assertEquals(getOccurrenceCount(gradleText, "com.android.support.constraint:constraint-layout"), 1);
  }

  @Test
  public void createLauncherActivity() {
    myConfigActivity.selectLauncherActivity();
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    myEditor.open(PROVIDED_MANIFEST);

    String text = myEditor.getCurrentFileContents();
    assertEquals(getOccurrenceCount(text, "android.intent.category.LAUNCHER"), 2);
  }

  @Test
  public void createActivityWithFragment() {
    myConfigActivity.selectUseFragment();
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish(Wait.seconds(15));

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
    assertThat(getOccurrenceCount(text, "android:name=\".MainActivity\"")).isEqualTo(1);
    assertThat(getOccurrenceCount(text, "android:parentActivityName=\".MyActivity\"")).isEqualTo(1);
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
  public void createActivityWithKotlin() throws Exception {
    myConfigActivity.setSourceLanguage("Kotlin");
    assertThat(getSavedRenderSourceLanguage()).isEqualTo(Language.KOTLIN);
    assertThat(getSavedKotlinSupport()).isFalse(); // Changing the Render source language should not affect the project default

    myDialog.clickFinish();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    myEditor
      .open("app/build.gradle")
      .moveBetween("apply plugin: 'kotlin-android'", "")
      .moveBetween("apply plugin: 'kotlin-android-extensions'", "")
      .moveBetween("implementation \"org.jetbrains.kotlin:kotlin-stdlib-jre7:$kotlin_", "version")
      .enterText("my_")
      .open("build.gradle")
      .moveBetween("ext.kotlin_", "version")
      .enterText("my_")
      .moveBetween("classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_", "version")
      .enterText("my_")
      .open("app/src/main/java/google/simpleapplication/MainActivity.kt")
      .moveBetween("override fun onCreate", "");

    // Add second Kotlin Activity and check it shouldn't add dependencies again (renamed $kotlin_version -> $kotlin_my_version)
    invokeNewActivityMenu();
    myConfigActivity.setSourceLanguage("Kotlin");
    myDialog.clickFinish();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    assertThat(myEditor.open("build.gradle").getCurrentFileContents()).doesNotContain("$kotlin_version");
    assertThat(myEditor.open("app/build.gradle").getCurrentFileContents()).doesNotContain("$kotlin_version");
  }

  @Test
  public void changeActivityName() {
    // Changing "Activity Name" causes "Title" and "Layout Name" to change
    myConfigActivity.enterTextFieldValue(ActivityTextField.NAME, "MainActivityTest");
    assertTextFieldValues("MainActivityTest", "activity_main_test", "MainActivityTest");

    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    guiTest.ideFrame().getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/MainActivityTest.java",
      "app/src/main/res/layout/activity_main_test.xml"
    );
  }

  @Test
  public void changeActivityNameUndo() throws Exception {
    // Changing "Activity Name" causes "Title" and "Layout Name" to change
    myConfigActivity.enterTextFieldValue(ActivityTextField.NAME, "MainActivityTest");
    assertTextFieldValues("MainActivityTest", "activity_main_test", "MainActivityTest");

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
    assertTextFieldValues("MainActivityTest1", "activity_main_test1", "MainActivityTest1");

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
    assertTextFieldValues("MainActivityTest123", "activity_main_test123", "Main Activity Test3");

    // Undo "Activity Name", should update "Activity Name" and LAYOUT, but not the title
    myConfigActivity.undoTextFieldValue(ActivityTextField.NAME);
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, "Main Activity Test3");

    // And undo of "Title" should bring everything to its default values
    myConfigActivity.undoTextFieldValue(ActivityTextField.TITLE);
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME, DEFAULT_ACTIVITY_TITLE);

    myDialog.clickCancel();
  }

  @Test
  public void projectViewPaneNotChanged() throws Exception {
    // Verify that after creating a new activity, the current pane on projectView does not change, assumes initial pane is ProjectView
    myDialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(PROVIDED_ACTIVITY);

    assertEquals(ProjectViewPane.ID, guiTest.ideFrame().getProjectView().getCurrentViewId());

    // Verify that Android stays on Android
    verifyNewActivityProjectPane(AndroidProjectViewPane.ID, "Android", true);

    // Now when new activity is cancelled
    verifyNewActivityProjectPane(ProjectViewPane.ID, "Project", false);
    verifyNewActivityProjectPane(AndroidProjectViewPane.ID, "Android", false);
  }

  // Note: This should be called only when the last open file was a Java/Kotlin file
  private void invokeNewActivityMenu() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Activity", "Basic Activity");
    myDialog = NewActivityWizardFixture.find(guiTest.ideFrame());

    myConfigActivity = myDialog.getConfigureActivityStep();
  }

  private void assertTextFieldValues(@NotNull String activityName, @NotNull String layoutName, @NotNull String title) {
    assertThat(myConfigActivity.getTextFieldValue(ActivityTextField.NAME)).isEqualTo(activityName);
    assertThat(myConfigActivity.getTextFieldValue(ActivityTextField.LAYOUT)).isEqualTo(layoutName);
    assertThat(myConfigActivity.getTextFieldValue(ActivityTextField.TITLE)).isEqualTo(title);
  }

  private void verifyNewActivityProjectPane(@NotNull String viewId, @NotNull String name, boolean finish) {
    // Change to viewId
    guiTest.ideFrame().getProjectView().selectPane(viewId, name);
    myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(PROVIDED_ACTIVITY);

    // Create a new activity
    guiTest.ideFrame().invokeMenuPath("File", "New", "Activity", "Basic Activity");
    myDialog = NewActivityWizardFixture.find(guiTest.ideFrame());
    myConfigActivity = myDialog.getConfigureActivityStep();
    if (finish) {
      myDialog.clickFinish();
      guiTest.ideFrame().waitForGradleProjectSyncToFinish();
      myEditor = guiTest.ideFrame().getEditor();
      myEditor.open(PROVIDED_ACTIVITY);

    }
    else {
      myDialog.clickCancel();
    }

    // Make sure it is still the same
    assertEquals(viewId, guiTest.ideFrame().getProjectView().getCurrentViewId());
  }

  private static boolean getSavedKotlinSupport() {
    return PropertiesComponent.getInstance().isTrueValue("SAVED_PROJECT_KOTLIN_SUPPORT");
  }

  private static void setSavedKotlinSupport(boolean isSupported) {
    PropertiesComponent.getInstance().setValue("SAVED_PROJECT_KOTLIN_SUPPORT", isSupported);
  }

  @NotNull
  private static Language getSavedRenderSourceLanguage() {
      return Language.fromName(PropertiesComponent.getInstance().getValue("SAVED_RENDER_LANGUAGE"), Language.JAVA);
  }

  private static void setSavedRenderSourceLanguage(Language language) {
    PropertiesComponent.getInstance().setValue("SAVED_RENDER_LANGUAGE", language.getName());
  }
}
