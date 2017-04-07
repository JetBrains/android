/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.idea.editors.theme.NewStyleDialog;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComboBox;
import javax.swing.JTextField;

public class NewStyleDialogFixture extends IdeaDialogFixture<NewStyleDialog> {
  private NewStyleDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<NewStyleDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public static NewStyleDialogFixture find(@NotNull Robot robot) {
    return new NewStyleDialogFixture(robot, find(robot, NewStyleDialog.class));
  }

  @NotNull
  public JTextComponentFixture getNewNameTextField() {
    return new JTextComponentFixture(robot(), robot().finder().findByType(this.target(), JTextField.class));
  }

  @NotNull
  public JComboBoxFixture getParentComboBox() {
    return new JComboBoxFixture(robot(), robot().finder().findByType(this.target(), JComboBox.class));
  }

  public void clickOk() {
    GuiTests.findAndClickOkButton(this);
  }
}
