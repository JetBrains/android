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
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.instantapp.SdkReplacer;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.npw.FormFactor.*;
import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.PROJECT_WIZARD)
@RunWith(GuiTestRunner.class)
public class NewProjectMultiModuleTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void createAllModule() {
    create(false, MOBILE, WEAR, TV, CAR, THINGS);
  }

  @Test
  public void createAllModuleWithIApp() {
    try {
      SdkReplacer.replaceSdkLocationAndActivate(null, true);
      create(true, MOBILE, WEAR, TV, CAR, THINGS);
    }
    finally {
      SdkReplacer.putBack();
    }

    EditorFixture editor = guiTest.ideFrame().getEditor();
    String appBuildGradle = editor.open("app/build.gradle").getCurrentFileContents();
    assertThat(appBuildGradle).contains("wearApp project(':wear')");
    assertThat(appBuildGradle).contains("implementation 'com.google.android.gms:play-services-wearable");

    String featureBuildGradle = editor.open("feature/build.gradle").getCurrentFileContents();
    assertThat(featureBuildGradle).doesNotContain("wearApp");
    assertThat(featureBuildGradle).doesNotContain("play-services-wearable");
  }

  /**
   * Creates a new project for the specified {@link FormFactor}
   */
  private void create(boolean selectInstantApp, @NotNull FormFactor... formFactors) {
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

    if (selectInstantApp) {
      newProjectWizard.getConfigureFormFactorStep().selectInstantAppSupport(MOBILE);
      newProjectWizard.clickNext(); // Skip "Configure the Feature" step
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
