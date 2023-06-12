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

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.DialogWrapper;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import org.fest.swing.cell.JComboBoxCellReader;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

public class ChooseProcessDialogFixture extends IdeaDialogFixture<DialogWrapper> {

  @NotNull
  public static ChooseProcessDialogFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    String title = "Choose Process";
    return new ChooseProcessDialogFixture(
        ideFrameFixture,
        find(ideFrameFixture.robot(), DialogWrapper.class, Matchers.byTitle(JDialog.class, title)));
    }

  private final IdeFrameFixture myIdeFrameFixture;

  private ChooseProcessDialogFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull DialogAndWrapper<DialogWrapper> dialogAndWrapper) {
    super(ideFrameFixture.robot(), dialogAndWrapper);
    myIdeFrameFixture = ideFrameFixture;
  }

  private static final JComboBoxCellReader DEBUGGER_PICKER_READER =
    (jComboBox, index) -> (GuiQuery.getNonNull(() -> ((AndroidDebugger)jComboBox.getItemAt(index)).getDisplayName()));

  @NotNull
  public ChooseProcessDialogFixture selectDebuggerType(@NotNull String typeName) {
    JComboBoxFixture comboBoxFixture =
        new JComboBoxFixture(robot(), robot().finder().findByType(target(), JComboBox.class));
    comboBoxFixture.replaceCellReader(DEBUGGER_PICKER_READER);
    comboBoxFixture.selectItem(typeName);
    return this;
  }

  public IdeFrameFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    return myIdeFrameFixture;
  }
}
