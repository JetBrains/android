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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.npw.FormFactor.*;

@RunIn(TestGroup.UNRELIABLE)  // b/63508325 @RunIn(TestGroup.PROJECT_WIZARD)
@RunWith(GuiTestRunner.class)
public class NewProjectMultiModuleTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void createAllModule() {
    create(MOBILE, WEAR, TV, CAR, THINGS);
  }

  /**
   * Creates a new project for the specified {@link FormFactor}
   */
  private void create(@NotNull FormFactor... formFactors) {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    newProjectWizard.getConfigureAndroidProjectStep()
      .enterApplicationName("TestApp")
      .enterCompanyDomain("my.test.domain")
      .enterPackageName("my.test");
    newProjectWizard.clickNext();

    for (FormFactor formFactor : formFactors) {
        newProjectWizard.getConfigureFormFactorStep().selectMinimumSdkApi(formFactor, "25");
    }

    for (FormFactor formFactor : formFactors) {
      newProjectWizard
        .clickNext() // Skip "Add Activity" step
        .assertStepIcon(formFactor.getIcon())
        .clickNext() // Skip "Configure Activity" step
        .assertStepIcon(formFactor.getIcon());
    }

    newProjectWizard.clickFinish();
    guiTest.ideFrame().waitForGradleImportProjectSync();
  }
}
