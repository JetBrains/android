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
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  private static final Logger LOGGER = Logger.getInstance(VerifyNpwPhoneAndTabletTemplatesTest.class);

  private List<String> expectedTemplates = List.of("No Activity", "Basic Activity", "Basic Activity (Material3)",
                                                   "Bottom Navigation Activity", "Empty Compose Activity", "Empty Compose Activity (Material3)",
                                                   "Empty Activity", "Fullscreen Activity", "Google AdMob Ads Activity", "Google Maps Activity",
                                                   "Google Pay Activity", "Login Activity", "Primary/Detail Flow", "Navigation Drawer Activity",
                                                   "Responsive Activity", "Settings Activity", "Scrolling Activity", "Tabbed Activity",
                                                   "Fragment + ViewModel", "Game Activity (C++)", "Native C++");

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
  public void testTemplateBuild() throws InterruptedException {

    for (String templateName : expectedTemplates) {
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
        }
        newProjectWizard.clickFinish(Wait.seconds(15), Wait.seconds(240));
        guiTest.robot().waitForIdle();
        boolean isGradleSyncSuccessful = guiTest.ideFrame().waitForGradleSyncToFinish(Wait.seconds(180));

        if (!isGradleSyncSuccessful) {
          failedGradleSyncTemplates.add(templateName);
        }

        GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(10)));

        guiTest.ideFrame().focus();
        guiTest.ideFrame().invokeMenuPath("Help");
        guiTest.ideFrame().invokeMenuPath("Help");

        guiTest.robot().waitForIdle();

        //Some templates have missing dependencies. Hence catching those for reporting purpose.
        int initialPopups = guiTest.robot().finder().findAll(Matchers.byTitle(JDialog.class, "Add Project Dependency").andIsShowing()).size();
        if(initialPopups > 0) {
          System.out.println("\nDependency missing for: " + templateName);
          MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency").clickCancel();
          MessagesFixture.findByTitle(guiTest.robot(), "Failed to Add Dependency").clickOk();
          dependencyMissingTemplates.add(templateName);
        }

        boolean buildSuccessful = guiTest.ideFrame().invokeProjectMake().isBuildSuccessful();

        if (!buildSuccessful) {
          failedBuildTemplates.add(templateName);
        }
        guiTest.ideFrame().closeProject();
        LOGGER.info("Validation complete for : " + templateName);
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
}
