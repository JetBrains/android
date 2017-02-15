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

import com.android.tools.idea.run.editor.AndroidDebugger;
import com.intellij.execution.impl.EditConfigurationsDialog;
import org.fest.swing.cell.JComboBoxCellReader;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;

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
    findAndClickLabelWhenEnabled(this, "Debugger");
    return this;
  }

  private static final JComboBoxCellReader DEBUGGER_PICKER_READER =
    (jComboBox, index) -> (GuiQuery.getNonNull(() -> ((AndroidDebugger)jComboBox.getItemAt(index)).getDisplayName()));

  @NotNull
  public EditConfigurationsDialogFixture selectAutoDebugger() {
    clickDebugger();
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(robot(), robot().finder().findByType(target(), JComboBox.class));
    comboBoxFixture.replaceCellReader(DEBUGGER_PICKER_READER);
    comboBoxFixture.selectItem("Auto");
    return this;
  }

  @NotNull
  public void clickOk() {
    findAndClickOkButton(this);
  }

}
