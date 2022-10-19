/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.text.StringUtil.getOccurrenceCount;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureBasicActivityStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateDefaultActivityTest {
  private static final String PROVIDED_ACTIVITY = "app/src/main/java/google/simpleapplication/MyActivity.java";
  private static final String PROVIDED_MANIFEST = "app/src/main/AndroidManifest.xml";
  private static final String APP_BUILD_GRADLE = "app/build.gradle";
  private static final String DEFAULT_ACTIVITY_NAME = "MainActivity";
  private static final String DEFAULT_LAYOUT_NAME = "activity_main";

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private EditorFixture myEditor;
  private NewActivityWizardFixture myDialog;
  private ConfigureBasicActivityStepFixture<NewActivityWizardFixture> myConfigActivity;

  @Before
  public void setUp() throws IOException {
    guiTest.importProjectWithSharedIndexAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(300));
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
    assertTextFieldValues(DEFAULT_ACTIVITY_NAME, DEFAULT_LAYOUT_NAME);
    assertThat(getSavedKotlinSupport()).isFalse();
    assertThat(getSavedRenderSourceLanguage()).isEqualTo(Language.Java);
  }

  /**
   * Verifies that a new activity can be created through the Wizard
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 9ab45c50-1eb0-44aa-95fb-17835baf2274
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Right click on the application module and select New > Activity > Basic Activity
   *   2. Enter activity and package name. Click Finish
   *   Verify:
   *   Activity class and layout.xml files are created. The activity previews correctly in layout editor.
   *   </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void createDefaultActivity() {
    myDialog.clickFinishAndWaitForSyncToFinish(Wait.seconds(120));

    guiTest.ideFrame().getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/MainActivity.java",
      "app/src/main/res/layout/activity_main.xml"
    );

    String manifesText = guiTest.getProjectFileText(PROVIDED_MANIFEST);
    assertEquals(1, getOccurrenceCount(manifesText, "android:name=\".MainActivity\""));
    assertEquals(1, getOccurrenceCount(manifesText, "@string/title_activity_main"));
    assertEquals(1, getOccurrenceCount(manifesText, "android.intent.category.LAUNCHER"));

    String gradleText = guiTest.getProjectFileText(APP_BUILD_GRADLE);
    assertEquals(1, getOccurrenceCount(gradleText, "com.android.support.constraint:constraint-layout"));
  }

  // Note: This should be called only when the last open file was a Java/Kotlin file
  private void invokeNewActivityMenu() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Activity", "Basic Views Activity");
    myDialog = NewActivityWizardFixture.find(guiTest.ideFrame());

    myConfigActivity = myDialog.getConfigureActivityStep("Basic Views Activity");
  }

  private void assertTextFieldValues(@NotNull String activityName, @NotNull String layoutName) {
    assertThat(myConfigActivity.getTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.NAME)).isEqualTo(activityName);
    assertThat(myConfigActivity.getTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.LAYOUT)).isEqualTo(layoutName);
  }

  private static boolean getSavedKotlinSupport() {
    return PropertiesComponent.getInstance().isTrueValue("SAVED_PROJECT_KOTLIN_SUPPORT");
  }

  @NotNull
  private static Language getSavedRenderSourceLanguage() {
    return Language.fromName(PropertiesComponent.getInstance().getValue("SAVED_RENDER_LANGUAGE"), Language.Java);
  }
}
