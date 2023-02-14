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
package com.android.tools.idea.tests.gui.npw;

import static com.google.common.truth.Truth.assertThat;


import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ChooseAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test following scenarios for NPW => Phone and Tablet tab
 * 1. Expected templates are displayed
 * 2. Correct default template is present
 * 3. For all expected templates (Except C++ and Navigation templates)
 * 3.a. Verify Gradle sync is successful
 * 3.b. Build -> Make Project is successful
 */


@RunWith(GuiTestRemoteRunner.class)
public class VerifyNpwPhoneAndTabletTemplatesTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(20, TimeUnit.MINUTES);

  private List<String> expectedTemplates = List.of("No Activity", "Empty Activity", "Basic Views Activity",
                                                   "Bottom Navigation Views Activity", "Empty Views Activity", "Navigation Drawer Views Activity",
                                                   "Responsive Views Activity", "Game Activity (C++)", "Native C++");

  private String defaultActivity = "Empty Activity";
  FormFactor selectMobileTab = FormFactor.MOBILE;

  @Test
  public void testAvailableTemplates() {
    ChooseAndroidProjectStepFixture androidProjectStep = guiTest.welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .selectTab(selectMobileTab);

    List<String> observedTemplates = androidProjectStep.listActivities(); //Get list of templates
    androidProjectStep.clickCancel(); //Close New Project dialog
    assertThat(observedTemplates).isEqualTo(expectedTemplates); //Verify expected templates are displayed for Phone and Tablet
  }

  @Test
  public void testDefaultTemplate() {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .selectTab(selectMobileTab)
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .wizard();

    String actualActivityName = newProjectWizard.getActivityName(defaultActivity);
    System.out.println("\nObserved default activity " + actualActivityName);
    assertThat((actualActivityName).contains(defaultActivity)).isTrue(); //Verify expected default template

    newProjectWizard.clickFinishAndWaitForSyncToFinish(Wait.seconds(420));
    GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));
    guiTest.ideFrame().clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(guiTest.ideFrame().invokeProjectMake(Wait.seconds(180)).isBuildSuccessful()).isTrue();
    validateGradleFile(); //Validate Gradle file contains Material 3 dependencies
    validateMainActivity(); //Validate MainActivity has @Composable
  }

  @Test
  public void  testNoActivityTemplate() {
    boolean buildProjectStatus = NewProjectTestUtil.createNewProject(guiTest, selectMobileTab, expectedTemplates.get(0));
    assertThat(buildProjectStatus).isTrue();
  }

  @Test
  public void  testBasicViewsActivityTemplate() {
    boolean buildProjectStatus = NewProjectTestUtil.createNewProject(guiTest, selectMobileTab, expectedTemplates.get(2));
    assertThat(buildProjectStatus).isTrue();
    validateThemeFile("app/src/main/res/values/themes.xml");
  }

  @Test
  public void  testEmptyViewsActivityTemplate() {
    boolean buildProjectStatus = NewProjectTestUtil.createNewProject(guiTest, selectMobileTab, expectedTemplates.get(4));
    assertThat(buildProjectStatus).isTrue();
  }

  @Test
  public void  testResponsiveViewsActivityTemplate() {
    boolean buildProjectStatus = NewProjectTestUtil.createNewProject(guiTest, selectMobileTab, expectedTemplates.get(6));
    assertThat(buildProjectStatus).isTrue();
  }

  private void validateMainActivity() {
    String mainActivityContents = guiTest.getProjectFileText("app/src/main/java/com/example/myapplication/MainActivity.kt");
      assertThat((mainActivityContents).contains("@Composable")).isTrue();
  }

  private void validateGradleFile() {
    String buildGradleContents = guiTest.getProjectFileText("app/build.gradle");
      assertThat((buildGradleContents).contains("implementation 'androidx.compose.ui:ui")).isTrue();
      assertThat((buildGradleContents).contains("implementation 'androidx.compose.material3:")).isTrue();
      assertThat((buildGradleContents).contains("implementation 'androidx.compose.ui:ui-tooling-preview")).isTrue();
      assertThat((buildGradleContents).contains("debugImplementation 'androidx.compose.ui:ui-tooling")).isTrue();
  }

  private void validateThemeFile(String fileRelPath) {
    String themeFileContents = guiTest.getProjectFileText(fileRelPath);
      assertThat(themeFileContents.contains("Theme.Material3")).isTrue();
  }
}
