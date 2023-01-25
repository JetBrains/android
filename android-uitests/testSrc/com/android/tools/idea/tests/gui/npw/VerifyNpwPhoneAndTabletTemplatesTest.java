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
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ChooseAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.swing.JDialog;

import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class VerifyNpwPhoneAndTabletTemplatesTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(20, TimeUnit.MINUTES);

  private List<String> expectedTemplates = List.of("No Activity", "Empty Activity", "Basic Views Activity",
                                                   "Bottom Navigation Views Activity", "Empty Views Activity", "Navigation Drawer Views Activity",
                                                   "Responsive Views Activity", "Game Activity (C++)", "Native C++");

  private String defaultActivity = "Empty Activity";
  private List<String> material3Templates = List.of("Empty Activity", "Basic Views Activity");
  private List<String> failedBuildTemplates = new ArrayList<String>();
  private List<String> dependencyMissingTemplates = new ArrayList<String>();
  private List<String> failedGradleSyncTemplates = new ArrayList<String>();
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
    newProjectWizard.clickCancel(); //Close New Project dialog
    System.out.println("\nObserved default activity " + actualActivityName);
    assertThat((actualActivityName).contains(defaultActivity)).isTrue(); //Verify expected default template
  }

  @Test
  public void testTemplateBuild() throws InterruptedException {
    for (String templateName : expectedTemplates) {
      if (!templateName.toLowerCase(Locale.ROOT).contains("c++")) {
        System.out.println("\nValidating Build > Make Project for: " + templateName);

        NewProjectWizardFixture newProjectWizard = guiTest
          .welcomeFrame()
          .createNewProject()
          .getChooseAndroidProjectStep()
          .selectTab(selectMobileTab)
          .chooseActivity(templateName)
          .wizard()
          .clickNext()
          .getConfigureNewAndroidProjectStep()
          .wizard();

        if (templateName.toLowerCase(Locale.ROOT).contains("c++")) {
          newProjectWizard.clickNext();
          guiTest.robot().waitForIdle();
        }
        newProjectWizard.clickFinish(Wait.seconds(15), Wait.seconds(240));
        guiTest.robot().waitForIdle();
        boolean isGradleSyncSuccessful = guiTest.ideFrame().waitForGradleSyncToFinish(Wait.seconds(200));

        if (!isGradleSyncSuccessful) {
          failedGradleSyncTemplates.add(templateName);
          GuiTests.takeScreenshot(guiTest.robot(), "Gradle_sync_error_for_"+templateName);
        }

        GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));

        guiTest.ideFrame().focus();
        guiTest.robot().waitForIdle();
        guiTest.waitForBackgroundTasks();

        //Some templates have missing dependencies. Hence catching those for reporting purpose.
        int initialPopups = guiTest.robot().finder().findAll(Matchers.byTitle(JDialog.class, "Add Project Dependency").andIsShowing()).size();
        if(initialPopups > 0) {
          System.out.println("\nDependency missing for: " + templateName);
          MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency").clickCancel();
          MessagesFixture.findByTitle(guiTest.robot(), "Failed to Add Dependency").clickOk();
          dependencyMissingTemplates.add(templateName);
        }

        boolean buildSuccessful = guiTest.ideFrame().invokeProjectMake(Wait.seconds(180)).isBuildSuccessful();

        if (!buildSuccessful) {
          failedBuildTemplates.add(templateName);
          GuiTests.takeScreenshot(guiTest.robot(), "Build_make_error_for_"+templateName);
        }

        if(material3Templates.contains(templateName)) {
          System.out.println("\nValidating Material3 content for: " + templateName);
          if(templateName.equals("Basic Views Activity")) {
            validateThemeFile("app/src/main/res/values/themes.xml");
          }else {
            validateGradleFile(); //Validate Gradle file contains Material 3 dependencies
            validateMainActivity(); //Validate MainActivity has @Composable
          }
        }
        guiTest.ideFrame().closeProject();
      }
    }

    if(!dependencyMissingTemplates.isEmpty()){
      System.out.println("\n*** Dependency is missing for: " + Arrays.toString(dependencyMissingTemplates.toArray()) + " ***");
    }

    if(!failedGradleSyncTemplates.isEmpty()){
      System.out.println("\n\n*** Gradle sync is failing for: " + Arrays.toString(failedGradleSyncTemplates.toArray()) + " ***");
    }

    if(!failedBuildTemplates.isEmpty()){
      System.out.println("\n*** Make Project failed for: " + Arrays.toString(failedBuildTemplates.toArray()) + " ***");
    }
    assertThat(failedBuildTemplates.isEmpty()).isTrue();
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
