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
import org.fest.swing.driver.JTextComponentDriver;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AbstractWizardStepFixture extends ComponentFixture<JRootPane> {
  protected AbstractWizardStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(robot, target);
  }

  protected static JCheckBoxFixture getCheckBoxFixtureByLabel(Robot robot, JComponent target, @NotNull final String label) {
    JCheckBox checkBox = robot.finder().find(target, new GenericTypeMatcher<JCheckBox>(JCheckBox.class) {
      @Override
      protected boolean isMatching(JCheckBox component) {
        return label.equals(component.getText());
      }
    });
    return new JCheckBoxFixture(robot, checkBox);
  }

  protected static JComboBoxFixture getComboBoxFixtureByLabel(Robot robot, JComponent target, @NotNull String label) {
    JComboBox combo = robot.finder().findByLabel(target, label, JComboBox.class, true);
    return new JComboBoxFixture(robot, combo);
  }

  @NotNull
  protected JTextField findTextFieldWithLabel(@NotNull String label) {
    return robot.finder().findByLabel(target, label, JTextField.class, true);
  }

  protected void replaceText(@NotNull JTextField textField, @NotNull String text) {
    JTextComponentDriver driver = new JTextComponentDriver(robot);
    driver.selectAll(textField);
    driver.enterText(textField, text);
  }
}
