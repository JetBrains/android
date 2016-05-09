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
package com.android.tools.idea.tests.gui.framework.fixture.npw;


import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JTextComponentMatcher;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;


public class ConfigureBasicActivityStepFixture extends AbstractWizardStepFixture<ConfigureBasicActivityStepFixture> {

  /**
   * This is the list of labels used to find the right text input field.
   * There is no really good way to access this fields programmatically, as they are defined on the template files.
   * For example see: tools/base/templates/activities/BasicActivity/template.xml
   */
  public enum ActivityTextField {
    NAME("Activity Name:"),
    LAYOUT("Layout Name:"),
    TITLE("Title:"),
    HIERARCHICAL_PARENT("Hierarchical Parent:"),
    PACKAGE_NAME("Package name:");

    private final String labelText;

    ActivityTextField(String labelText) {
      this.labelText = labelText;
    }

    private String getLabelText() {
      return labelText;
    }
  }

  protected ConfigureBasicActivityStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ConfigureBasicActivityStepFixture.class, robot, target);
  }

  @NotNull
  public ConfigureBasicActivityStepFixture selectLauncherActivity() {
    findCheckBoxWithLabel("Launcher Activity").select();
    return this;
  }

  @NotNull
  public ConfigureBasicActivityStepFixture selectUseFragment() {
    findCheckBoxWithLabel("Use a Fragment").select();
    return this;
  }

  @NotNull
  public ConfigureBasicActivityStepFixture enterTextFieldValue(@NotNull ActivityTextField activityField, @NotNull String text) {
    // The label text may reference the input directly (a subclass of JTextComponent), or it may reference the container of the input
    // (for example ReferenceEditorComboWithBrowseButton (JPanel) or an EditorComboBox)
    JComponent comp = robot().finder().findByLabel(target(), activityField.getLabelText(), JComponent.class, true);
    JTextComponent textField = robot().finder().find(comp, JTextComponentMatcher.any());
    new JTextComponentFixture(robot(), textField).setText("").enterText(text);

    return this;
  }

  @NotNull
  public String getTextFieldValue(@NotNull ActivityTextField activityField) {
    return findTextFieldWithLabel(activityField.getLabelText()).getText();
  }

  @NotNull
  public ConfigureBasicActivityStepFixture undoTextFieldValue(@NotNull ActivityTextField activityField) {
    JTextField textField = findTextFieldWithLabel(activityField.getLabelText());
    robot().rightClick(textField);

    JMenuItem popup = GuiTests.waitUntilShowing(robot(), null, new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem menuItem) {
        return "Restore default value".equals(menuItem.getText());
      }
    });

    robot().click(popup);

    return this;
  }
}