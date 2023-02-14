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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test following scenarios for NPW => Android TV tab
 * 1. Expected templates are displayed
 * 2. Correct default template is present
 * 3. For all expected templates
 * 3.a. Verify Gradle sync is successful
 * 3.b. Build -> Make Project is successful
 */

@RunWith(GuiTestRemoteRunner.class)
public class VerifyNpwAndroidTVTemplatesTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private List<String> expectedTemplates = List.of("No Activity", "Blank Views Activity");
  private String defaultActivity = "Blank Views Activity";

  private List<String> failedBuildTemplates = new ArrayList<String>();
  private List<String> dependencyMissingTemplates = new ArrayList<String>();
  FormFactor selectTVTab = FormFactor.TV;

  @Test
  public void testAvailableTemplates() {
    ChooseAndroidProjectStepFixture androidProjectStep = guiTest.welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .selectTab(selectTVTab);

    List<String> observedTemplates = androidProjectStep.listActivities(); //Get list of templates
    androidProjectStep.clickCancel(); //Close New Project dialog
    assertThat(observedTemplates).isEqualTo(expectedTemplates); //Verify expected templates are displayed for Android TV
  }

  @Test
  public void testDefaultTemplate() {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .selectTab(selectTVTab)
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .wizard();

    String actualActivityName = newProjectWizard.getActivityName(defaultActivity);
    System.out.println("\nObserved default activity " + actualActivityName);
    assertThat(actualActivityName).contains(defaultActivity); //Verify expected default template

    newProjectWizard.clickFinishAndWaitForSyncToFinish(Wait.seconds(420));
    GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));
    guiTest.ideFrame().clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(guiTest.ideFrame().invokeProjectMake(Wait.seconds(180)).isBuildSuccessful()).isTrue();

  }

  @Test
  public void  testNoActivityTemplate() {
    boolean buildProjectStatus = NewProjectTestUtil.createNewProject(guiTest, selectTVTab, expectedTemplates.get(0));
    assertThat(buildProjectStatus).isTrue();
  }
}
