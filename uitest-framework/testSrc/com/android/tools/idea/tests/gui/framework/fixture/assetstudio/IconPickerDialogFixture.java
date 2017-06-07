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
package com.android.tools.idea.tests.gui.framework.fixture.assetstudio;

import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import com.android.tools.idea.npw.assetstudio.ui.IconPickerDialog;
import com.android.tools.idea.tests.gui.framework.fixture.SearchTextFieldFixture;
import com.intellij.ui.SearchTextField;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class IconPickerDialogFixture extends IdeaDialogFixture<IconPickerDialog> {

  @NotNull
  public static IconPickerDialogFixture find(@NotNull Robot robot) {
    return new IconPickerDialogFixture(robot, find(robot, IconPickerDialog.class));
  }

  private IconPickerDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<IconPickerDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public void clickOk() {
    findAndClickOkButton(this);
  }

  public IconPickerDialogFixture filterIconByName(@NotNull String name) {
    new SearchTextFieldFixture(robot(), robot().finder().findByType(this.target(), SearchTextField.class))
      .enterText(name);
    return this;
  }

  @NotNull
  public JTableFixture getIconTable() {
    return new JTableFixture(robot(), robot().finder().findByType(target(), JTable.class));
  }
}
