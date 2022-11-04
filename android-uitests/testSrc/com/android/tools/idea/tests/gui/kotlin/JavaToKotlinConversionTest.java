/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinWithAndroidWithGradleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ConvertJavaToKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.KotlinIsNotConfiguredDialogFixture;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbService;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.Assert.assertTrue;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

@RunWith(GuiTestRemoteRunner.class)
public class JavaToKotlinConversionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(9, TimeUnit.MINUTES);
  private File projectDir;
  private String studioVersion;
  private IdeFrameFixture ideFrame;
  private String projectName = "SimpleApplication";

  /**
   * Verifies it can convert Java class to Kotlin Class.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 7adb5104-9244-4cac-a1df-7d04991c8f14
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication project and wait for project sync to finish.
   *   2. Open up MyActivity.java file.
   *   3. Invoke Code > Convert Java to Kotlin
   *   4. Build the app.
   *   5. Verify 1, 2, 3.
   *   Verify:
   *   1. Ensure the code java class is converted to Kotlin.
   *   2. Check if the Activities are getting converted to Kotlin.
   *   3. App is built successfully.
   *   </pre>
   * <p>
   */

  @Before
  public void setUp() throws Exception {

    projectDir = guiTest.setUpProject(projectName, null, null, null, null);
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame = guiTest.ideFrame();
    studioVersion = ideFrame.getAndroidStudioVersion();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.clearNotificationsPresentOnIdeFrame();
  }

  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testJavaToKotlinConversion() throws Exception {
    EditorFixture editor = ideFrame.getEditor();
    String kotlinVersionTobeConfigured;

    // Clearing any notifications on the ideframe
    ideFrame.clearNotificationsPresentOnIdeFrame();

    ideFrame.getProjectView()
      .selectAndroidPane();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Kotlin Not configured dialog box opens, click ok
    ideFrame.waitAndInvokeMenuPath("Code", "Convert Java File to Kotlin File");
    KotlinIsNotConfiguredDialogFixture.find(ideFrame.robot())
      .clickOkAndWaitDialogDisappear();

    //Waiting for 'Configure Kotlin with Android with Gradle' dialog box
    ConfigureKotlinWithAndroidWithGradleDialogFixture
      configureKotlinDialogBox = ConfigureKotlinWithAndroidWithGradleDialogFixture.find(ideFrame);
    //Click on single module, and confirm if it displays the module name.
    ideFrame.takeScreenshot();
    assertTrue(configureKotlinDialogBox.clickRadioButtonWithName("Single module:"));
    List<String> modulesList = configureKotlinDialogBox.getSingleModuleComboBoxDetails();
    assertTrue(modulesList.size() > 0);
    //Click on all modules again.
    assertTrue(configureKotlinDialogBox.clickRadioButtonWithName("All modules"));
    //Check if multiple kotlin versions are present.
    List<String> KotlinVersionsList = configureKotlinDialogBox.getKotlinVersions();
    kotlinVersionTobeConfigured = KotlinVersionsList.get(0);
    assertTrue(kotlinVersionTobeConfigured != "1.0.0");
    assertTrue(KotlinVersionsList.size() > 2);
    //Click Ok
    configureKotlinDialogBox.clickOkAndWaitDialogDisappear();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertTrue(editor.open("build.gradle").getCurrentFileContents().contains(kotlinVersionTobeConfigured));

    //Open MainActivity and convert the file to kotlin.
    ideFrame.getEditor().open("app/src/main/java/google/simpleapplication/MyActivity.java");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.waitAndInvokeMenuPath("Code", "Convert Java File to Kotlin File");
    ConvertJavaToKotlinDialogFixture.find(ideFrame)
      .clickYes();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    Wait.seconds(60).expecting("Wait for kt file is generated.")
      .until(() -> "MyActivity.kt".equals(editor.getCurrentFileName()));
    assertTrue(editor.getCurrentFileName().contains(".kt"));
    assertThat(editor.getCurrentFileContents()).contains("class MyActivity : Activity() {");
    assertThat(editor.getHighlights(HighlightSeverity.ERROR)).isEmpty();

    //make and build file.
    ideFrame.requestProjectSyncAndWaitForSyncToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.invokeAndWaitForBuildAction(Wait.seconds(300), "Build", "Rebuild Project");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
}
