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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.InstantAppUrlFinder;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.android.tools.idea.npw.deprecated.NewFormFactorModulePath.setWHSdkLocation;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.System.getenv;

/**
 * Test that newly created Instant App projects do not have errors in them
 */
@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class NewInstantAppTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void before() {
    setWHSdkLocation("TestValue");
  }

  @After
  public void after() {
    setWHSdkLocation(getenv("WH_SDK"));
  }

  //Not putting this in before() as I plan to add some tests that work on non-default projects.
  private void createAndOpenDefaultAIAProject(@NotNull String projectName) {
    //TODO: There is some commonality between this code, the code in NewProjectTest and further tests I am planning, but there are also
    //      differences. Once AIA tests are completed this should be factored out into the NewProjectWizardFixture
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.getConfigureAndroidProjectStep();
    configureAndroidProjectStep
      .enterCompanyDomain("test.android.com")
      .enterApplicationName(projectName);
    guiTest.setProjectPath(configureAndroidProjectStep.getLocationInFileSystem());

    newProjectWizard
      .clickNext() // Complete project configuration
      .getConfigureFormFactorStep()
      .selectMinimumSdkApi(MOBILE, "16")
      .selectInstantAppSupport(MOBILE);

    newProjectWizard
      .clickNext() // Complete form factor configuration
      .clickNext() // Accept default values for Instant App Module
      .clickNext() // Skip "Add Activity" step
      .clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }

  @Test
  public void testValidPathInDefaultNewInstantAppProjects() throws IOException {
    createAndOpenDefaultAIAProject("RouteApp");

    Module module = guiTest.ideFrame().getModule("atom");
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertThat(facet).isNotNull();
    assertThat(new InstantAppUrlFinder(MergedManifest.get(facet)).getAllUrls()).isNotEmpty();
  }
}
