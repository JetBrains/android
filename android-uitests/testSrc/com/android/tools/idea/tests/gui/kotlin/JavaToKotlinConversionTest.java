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
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinWithAndroidWithGradleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ConvertJavaToKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.KotlinIsNotConfiguredDialogFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.List;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class JavaToKotlinConversionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(9, TimeUnit.MINUTES);
  private IdeFrameFixture ideFrame;

  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = 30;

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

    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Java);
    guiTest.robot().waitForIdle();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame = guiTest.ideFrame();

    //Clearing notifications present on the screen.
    ideFrame.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testJavaToKotlinConversion() throws Exception {
    EditorFixture editor = ideFrame.getEditor();

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

    //Taking screenshot to observe the 'Configure Kotlin with Android with Gradle' dialog box in case of failure.
    ideFrame.takeScreenshot();

    //Click on single module, and confirm if it displays the module name.
    assertThat(configureKotlinDialogBox.clickRadioButtonWithName("Single module:"))
      .isTrue();

    List<String> modulesList = configureKotlinDialogBox.getSingleModuleComboBoxDetails();
    assertThat(modulesList.size()).isGreaterThan(0);

    //Click on all modules again.
    assertThat(configureKotlinDialogBox.clickRadioButtonWithName("All modules"))
      .isTrue();

    /*
    Ignoring the below steps as kotlin plugin versions are obtained with a network request.
    Since the network access is unavailable, We need to handle setting
    the Kotlin plugin version manually as of now, till we find a fix for the issue.

    //Check if multiple kotlin versions are present.
    List<String> KotlinVersionsList = configureKotlinDialogBox.getKotlinVersions();
    String kotlinVersionTobeConfigured = KotlinVersionsList.get(0);

    assertThat(kotlinVersionTobeConfigured).isNotEqualTo("1.0.0");
    assertThat(KotlinVersionsList.size()).isGreaterThan(2);
     */

    //Click Ok with the default selected value and replacing the default "1.0.0" manually in build.gradle.kts file.
    configureKotlinDialogBox.clickOkAndWaitDialogDisappear();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertThat(editor.open("build.gradle.kts")
                 .getCurrentFileContents()
                 .contains("libs.plugins.org.jetbrains.kotlin.android"))
      .isTrue();

    //Manually changing the kotlin version to the latest version, and this step needs to be updated with every new release.
    ConversionTestUtil.changeKotlinVersion(guiTest);

    //Manually syncing after changing the kotlin version.
    ideFrame.requestProjectSyncAndWaitForSyncToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Taking screenshot to make sure kotlin version is updated and sync is successful.
    ideFrame.takeScreenshot();

    ideFrame.getEditor().open("app/src/main/java/android/com/app/MainActivity.java");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Converting MainActivity.java to kotlin file.
    ideFrame.waitAndInvokeMenuPath("Code", "Convert Java File to Kotlin File");
    ConvertJavaToKotlinDialogFixture.find(ideFrame)
      .clickYes();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Assertions to make sure that java to kotlin conversion is completed.
    assertThat(editor.getCurrentFileName()).contains(".kt");
    assertThat(editor.getCurrentFileContents()).contains("class MainActivity :");
    assertThat(editor.getHighlights(HighlightSeverity.ERROR)).isEmpty();

    //Invoking project sync.
    ideFrame.requestProjectSyncAndWaitForSyncToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Invoking project make.
    ideFrame.invokeAndWaitForBuildAction(Wait.seconds(300),
                                         "Build", "Rebuild Project");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
}
