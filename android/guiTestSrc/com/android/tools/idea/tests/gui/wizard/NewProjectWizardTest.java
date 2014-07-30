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
package com.android.tools.idea.tests.gui.wizard;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ChooseOptionsForNewFileStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.project.Project;
import org.junit.Test;

import java.io.File;

import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.idea.wizard.FormFactorUtils.FormFactor.MOBILE;
import static com.intellij.openapi.util.io.FileUtil.join;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;

public class NewProjectWizardTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testCreateNewMobileProject() {
    final String projectName = "Test Application";

    GeneralSettings.getInstance().setShowTipsOnStartup(false);

    WelcomeFrameFixture welcomeFrame = findWelcomeFrame();
    welcomeFrame.newProjectButton().click();

    NewProjectWizardFixture newProjectWizard = findNewProjectWizard();

    ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.configureAndroidProjectStep();
    configureAndroidProjectStep.enterApplicationName(projectName)
                               .enterCompanyDomain("Google")
                               .enterPackageName("com.google");
    File projectPath = configureAndroidProjectStep.getLocationInFileSystem();
    newProjectWizard.clickNext();

    String minSdkApi = "19";
    newProjectWizard.configureFormFactorStep().selectMinimumSdkApi(MOBILE, minSdkApi);
    newProjectWizard.clickNext();

    // Skip "Add Activity" step
    newProjectWizard.clickNext();

    ChooseOptionsForNewFileStepFixture chooseOptionsForNewFileStep = newProjectWizard.chooseOptionsForNewFileStep();
    chooseOptionsForNewFileStep.enterActivityName("MainActivity");
    String layoutName = chooseOptionsForNewFileStep.getLayoutName();
    newProjectWizard.clickFinish();

    IdeFrameFixture projectFrame = findIdeFrame(projectName, projectPath);
    projectFrame.waitForGradleProjectToBeOpened();

    Project project = projectFrame.getProject();
    String layoutFileName = layoutName + DOT_XML;
    File expectedToBeOpened = new File(join(project.getBasePath(), "app", "src", "main", "res", "layout", layoutFileName));

    projectFrame.waitForFileToBeOpenedAndSelected(expectedToBeOpened);

    // Verify state of project
    projectFrame.requireModuleCount(2);
    IdeaAndroidProject appAndroidProject = projectFrame.getAndroidProjectForModule("app");
    assertThat(appAndroidProject.getVariantNames()).as("variants").containsOnly("debug", "release");
    assertThat(appAndroidProject.getSelectedVariant().getName()).as("selected variant").isEqualTo("debug");

    AndroidProject model = appAndroidProject.getDelegate();
    ApiVersion minSdkVersion = model.getDefaultConfig().getProductFlavor().getMinSdkVersion();
    assertNotNull("minSdkVersion", minSdkVersion);
    assertThat(minSdkVersion.getApiString()).as("minSdkVersion API").isEqualTo(minSdkApi);
  }

  @Test @IdeGuiTest(closeProjectBeforeExecution = false)
  public void testSomething() {
    System.out.println("Hello");
  }
}
