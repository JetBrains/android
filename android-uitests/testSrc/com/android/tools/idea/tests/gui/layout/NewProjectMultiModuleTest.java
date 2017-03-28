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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.npw.FormFactor.*;

@RunWith(GuiTestRunner.class)
@RunIn(TestGroup.UNRELIABLE)
public class NewProjectMultiModuleTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void createMobileWear() {
    create(MOBILE, WEAR);
  }

  @Test
  public void createMobileTV() {
    create(MOBILE, TV, CAR);
  }

  @Test
  public void createMobileWearTv() {
    create(MOBILE, WEAR, TV);
  }

  @Test
  public void createMobileCar() {
    create(MOBILE, CAR);
  }

  @Test
  public void createMobileWearTvCar() {
    create(MOBILE, WEAR, TV, CAR);
  }

  /**
   * Creates a new project for the specified {@link FormFactor}
   */
  private void create(@NotNull FormFactor... formFactors) {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.getConfigureAndroidProjectStep();
    configureAndroidProjectStep.enterApplicationName("TestApp").enterCompanyDomain("my.test.domain").enterPackageName("my.test");
    guiTest.setProjectPath(configureAndroidProjectStep.getLocationInFileSystem());
    newProjectWizard.clickNext();

    for (FormFactor formFactor : formFactors) {
        newProjectWizard.getConfigureFormFactorStep().selectMinimumSdkApi(formFactor, "23");
    }

    for (FormFactor ignored : formFactors) {
      newProjectWizard.clickNext(); // Skip "Add Activity" step
      newProjectWizard.clickNext(); // Skip "Configure Activity" step
    }

    newProjectWizard.clickFinish();
    guiTest.ideFrame().waitForGradleImportProjectSync();
  }
}
