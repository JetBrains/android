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
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.progress.ProgressManager;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NewProjectWizardFixture extends AbstractWizardFixture<NewProjectWizardFixture> {
  @NotNull
  public static NewProjectWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Create New Project"));
    return new NewProjectWizardFixture(robot, dialog);
  }

  private NewProjectWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(NewProjectWizardFixture.class, robot, target);
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture getConfigureAndroidProjectStep() {
    JRootPane rootPane = findStepWithTitle("Create Android Project");
    return new ConfigureAndroidProjectStepFixture(robot(), rootPane);
  }

  @NotNull
  public ConfigureFormFactorStepFixture getConfigureFormFactorStep() {
    JRootPane rootPane = findStepWithTitle("Target Android Devices");
    return new ConfigureFormFactorStepFixture(robot(), rootPane);
  }

  public ConfigureInstantModuleStepFixture getConfigureInstantModuleStep() {
    JRootPane rootPane = findStepWithTitle("Configure the feature module");
    return new ConfigureInstantModuleStepFixture(robot(), rootPane);
  }

  @NotNull
  public ConfigureCppStepFixture getConfigureCppStepFixture() {
    JRootPane rootPane = findStepWithTitle("Customize C++ Support");
    return new ConfigureCppStepFixture(robot(), rootPane);
  }

  public NewProjectWizardFixture chooseActivity(@NotNull String activity) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class));
    listFixture.replaceCellReader((jList, index) -> String.valueOf(jList.getModel().getElementAt(index)));
    listFixture.clickItem(activity);
    return this;
  }

  @NotNull
  public ChooseOptionsForNewFileStepFixture getChooseOptionsForNewFileStep() {
    JRootPane rootPane = findStepWithTitle(StudioFlags.NPW_NEW_PROJECT.get() ? "Configure Activity" : "Customize the Activity");
    return new ChooseOptionsForNewFileStepFixture(robot(), rootPane);
  }

  @NotNull
  @Override
  public NewProjectWizardFixture clickFinish() {
    super.clickFinish();

    // Wait for gradle project importing to finish
    // b/66170375
    Wait.seconds(60).expecting("Modal Progress Indicator to finish")
      .until(() -> {
        robot().waitForIdle();
        return !ProgressManager.getInstance().hasModalProgressIndicator();
      });
    return myself();
  }
}
