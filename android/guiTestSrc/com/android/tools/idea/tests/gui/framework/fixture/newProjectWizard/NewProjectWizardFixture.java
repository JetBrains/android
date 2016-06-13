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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NewProjectWizardFixture extends AbstractWizardFixture<NewProjectWizardFixture> {
  @NotNull
  public static NewProjectWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return "Create New Project".equals(dialog.getTitle());
      }
    });
    return new NewProjectWizardFixture(robot, dialog);
  }

  private NewProjectWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(NewProjectWizardFixture.class, robot, target);
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture getConfigureAndroidProjectStep() {
    JRootPane rootPane = findStepWithTitle("Configure your new project");
    return new ConfigureAndroidProjectStepFixture(robot(), rootPane);
  }

  @NotNull
  public ConfigureFormFactorStepFixture getConfigureFormFactorStep() {
    JRootPane rootPane = findStepWithTitle("Select the form factors your app will run on");
    return new ConfigureFormFactorStepFixture(robot(), rootPane);
  }

  @NotNull
  public ChooseOptionsForNewFileStepFixture getChooseOptionsForNewFileStep() {
    JRootPane rootPane = findStepWithTitle("Customize the Activity");
    return new ChooseOptionsForNewFileStepFixture(robot(), rootPane);
  }
}
