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
package com.android.tools.idea.tests.gui.framework.fixture;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickLabel;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitTreeForPopup;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.module.Module;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import org.fest.swing.cell.JComboBoxCellReader;
import org.fest.swing.cell.JTreeCellReader;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;

public class EditConfigurationsDialogFixture extends IdeaDialogFixture<EditConfigurationsDialog> {
  @NotNull
  public static EditConfigurationsDialogFixture find(@NotNull Robot robot) {
    return new EditConfigurationsDialogFixture(robot, find(robot, EditConfigurationsDialog.class));
  }

  private EditConfigurationsDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<EditConfigurationsDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  private EditConfigurationsDialogFixture clickDebugger() {
    findAndClickLabel(this, "Debugger");
    return this;
  }

  private static final JComboBoxCellReader DEBUGGER_PICKER_READER =
    (jComboBox, index) -> (GuiQuery.getNonNull(() -> ((AndroidDebugger)jComboBox.getItemAt(index)).getDisplayName()));

  private static final JTreeCellReader CONFIGURATION_CELL_READER = (jList, value) ->
    value.toString();

  private static final JComboBoxCellReader MODULE_PICKER_READER = (jComboBox, index) -> {
    Object element = jComboBox.getItemAt(index);
    if (element != null) {
      return ((Module)element).getName();
    }
    return null; // The element at index 0 is null. Deal with it specially.
  };

  @NotNull
  public EditConfigurationsDialogFixture selectDebuggerType(@NotNull String typeName) {
    clickDebugger();
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(robot(), robot().finder().findByType(target(), JComboBox.class));
    comboBoxFixture.replaceCellReader(DEBUGGER_PICKER_READER);
    comboBoxFixture.selectItem(typeName);
    return this;
  }

  public void clickOk() {
    findAndClickOkButton(this);
    waitUntilNotShowing();
  }

  @NotNull
  public EditConfigurationsDialogFixture clickAddNewConfigurationButton() {
    ActionButton addNewConfigurationButton = robot().finder().find(target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        if (!button.isShowing()) return false;
        AnAction buttonAction = button.getAction();
        if (buttonAction == null) return false;
        Presentation presentation = buttonAction.getTemplatePresentation();
        if (presentation == null) return false;
        return "Add New Configuration".equals(presentation.getText());
      }
    });
    robot().click(addNewConfigurationButton);
    return this;
  }

  @NotNull
  public EditConfigurationsDialogFixture selectConfigurationType(@NotNull String confTypeName) {
    JTreeFixture listFixture= new JTreeFixture(robot(), waitTreeForPopup(robot()));
    listFixture.replaceCellReader(CONFIGURATION_CELL_READER);
    listFixture.clickPath(confTypeName);
    return this;
  }

  @NotNull
  public EditConfigurationsDialogFixture enterAndroidInstrumentedTestConfigurationName(@NotNull String text) {
    JTextField textField = robot().finder().findByLabel(target(), "Name:", JTextField.class, true);
    new JTextComponentFixture(robot(), textField).deleteText().enterText(text);
    return this;
  }

  @NotNull
  public EditConfigurationsDialogFixture selectModuleForAndroidInstrumentedTestsConfiguration(@NotNull String moduleName) {
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(robot(), robot().finder().findByType(ModulesComboBox.class, true));

    comboBoxFixture.replaceCellReader(MODULE_PICKER_READER);
    comboBoxFixture.selectItem(moduleName);
    return this;
  }

  @NotNull
  public EditConfigurationsDialogFixture selectDeployAsInstantApp(boolean selected) {
    JCheckBox instantAppCheckbox = waitUntilShowing(robot(), target(), Matchers.byText(JCheckBox.class, "Deploy as instant app"));
    new JCheckBoxFixture(robot(), instantAppCheckbox).setSelected(selected);
    return this;
  }
}
