/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.junit.Test;

import java.io.File;

import static com.android.tools.idea.wizard.FormFactorUtils.FormFactor.MOBILE;

public class NewProjectWizardTest extends GuiTestCase {
  @Test
  public void testCreateNewProject() {
    final String projectName = "Test Application";

    WelcomeFrameFixture welcomeFrame = findWelcomeFrame();
    welcomeFrame.newProjectButton().click();

    NewProjectWizardFixture newProjectWizard = findNewProjectWizard();

    ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.configureAndroidProjectStep();
    configureAndroidProjectStep.enterApplicationName(projectName)
                               .enterCompanyDomain("Google")
                               .enterPackageName("com.google");
    File projectPath = configureAndroidProjectStep.getLocationInFileSystem();
    newProjectWizard.clickNext();

    newProjectWizard.configureFormFactorStep().selectMinimumSdkApi(MOBILE, "19");
    newProjectWizard.clickNext();

    // Skip "Add Activity" step
    newProjectWizard.clickNext();

    newProjectWizard.chooseOptionsForNewFileStep().enterActivityName("MainActivity");
    newProjectWizard.clickFinish();

    IdeFrameFixture projectFrame = findIdeFrame(projectName, projectPath);
    projectFrame.waitForProjectSyncToFinish();
  }
}
