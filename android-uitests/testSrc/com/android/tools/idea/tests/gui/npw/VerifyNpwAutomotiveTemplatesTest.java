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
import com.android.tools.idea.tests.gui.framework.fixture.npw.ChooseAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import org.fest.swing.timing.Pause;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class VerifyNpwAutomotiveTemplatesTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private List<String> expectedTemplates = List.of("No Activity", "Media Service", "Messaging Service");

  private List<String> failedBuildTemplates = new ArrayList<String>();
  private List<String> dependencyMissingTemplates = new ArrayList<String>();
  FormFactor selectAutomotiveTab = FormFactor.AUTOMOTIVE;

  @Test
  public void testAvailableTemplates() {

    ChooseAndroidProjectStepFixture androidProjectStep = guiTest.welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .selectTab(selectAutomotiveTab);

    List<String> observedTemplates = androidProjectStep.listActivities(); //Get list of templates
    androidProjectStep.clickCancel(); //Close New Project dialog
    assertThat(observedTemplates).isEqualTo(expectedTemplates); //Verify expected templates are displayed for Phone and Tablet
  }

  @Test
  public void testTemplateBuild() {

    for (String templateName : expectedTemplates) {
        System.out.println("\nValidating Build > Make Project for: " + templateName);

        NewProjectWizardFixture newProjectWizard = guiTest
          .welcomeFrame()
          .createNewProject()
          .getChooseAndroidProjectStep()
          .selectTab(selectAutomotiveTab)
          .chooseActivity(templateName)
          .wizard()
          .clickNext()
          .getConfigureNewAndroidProjectStep()
          .wizard();

        if (templateName.toLowerCase(Locale.ROOT).contains("c++")) {
          newProjectWizard.clickNext();
        }
        newProjectWizard.clickFinishAndWaitForSyncToFinish();

        Collection<JPopupMenu> popups;
        int counter = 100; // Wait approx 1 second for a popup to appear on hover/click.
        do {
          popups = guiTest.robot().finder().findAll(Matchers.byType(JPopupMenu.class).andIsShowing());
          if (popups.size() > 0 ) {
            JButton okButton = guiTest.robot().finder().find(Matchers.byText(JButton.class, "OK"));
            guiTest.robot().click(okButton);
            dependencyMissingTemplates.add(templateName);
          }
          Pause.pause();
        }
        while (counter-- > 0);

        guiTest.waitForAllBackgroundTasksToBeCompleted();

        boolean buildSuccessful = guiTest.ideFrame().invokeProjectMake().isBuildSuccessful();

        if (!buildSuccessful) {
          failedBuildTemplates.add(templateName);
        }
        guiTest.ideFrame().closeProject();

    }

    if(!dependencyMissingTemplates.isEmpty()){
      System.out.println("\n*** Dependency is missing for: " + Arrays.toString(dependencyMissingTemplates.toArray()) + " ***");
    }

    if(!failedBuildTemplates.isEmpty()){
      System.out.println("\n*** Make Project failed for: " + Arrays.toString(failedBuildTemplates.toArray()) + " ***");
    }
    assertThat(failedBuildTemplates.isEmpty()).isTrue();
  }
}
