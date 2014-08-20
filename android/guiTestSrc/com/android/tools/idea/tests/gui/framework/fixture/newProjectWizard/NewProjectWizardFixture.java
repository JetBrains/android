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

import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class NewProjectWizardFixture extends ComponentFixture<JDialog> {
  @NotNull
  public static NewProjectWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = robot.finder().find(new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        return "Create New Project".equals(dialog.getTitle());
      }
    });
    return new NewProjectWizardFixture(robot, dialog);
  }

  private NewProjectWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(robot, target);
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture getConfigureAndroidProjectStep() {
    JRootPane rootPane = findStepWithTitle("Configure your new project");
    return new ConfigureAndroidProjectStepFixture(robot, rootPane);
  }

  @NotNull
  public ConfigureFormFactorStepFixture getConfigureFormFactorStep() {
    JRootPane rootPane = findStepWithTitle("Select the form factors your app will run on");
    return new ConfigureFormFactorStepFixture(robot, rootPane);
  }

  @NotNull
  public ChooseOptionsForNewFileStepFixture getChooseOptionsForNewFileStep() {
    JRootPane rootPane = findStepWithTitle("Choose options for your new file");
    return new ChooseOptionsForNewFileStepFixture(robot, rootPane);
  }

  @NotNull
  private JRootPane findStepWithTitle(@NotNull final String title) {
    final JRootPane rootPane = target.getRootPane();
    Pause.pause(new Condition("'Panel with title \"" + title + "\" is visible'") {
      @Override
      public boolean test() {
        Collection<JLabel> found = robot.finder().findAll(rootPane, JLabelMatcher.withText(title).andShowing());
        return !found.isEmpty();
      }
    });
    return rootPane;
  }

  @NotNull
  public NewProjectWizardFixture clickNext() {
    JButton button = findButtonByText("Next");
    robot.click(button);
    return this;
  }

  @NotNull
  public NewProjectWizardFixture clickFinish() {
    JButton button = findButtonByText("Finish");
    robot.click(button);
    return this;
  }

  @NotNull
  private JButton findButtonByText(@NotNull String text) {
    return robot.finder().find(target, JButtonMatcher.withText(text).andShowing());
  }
}
