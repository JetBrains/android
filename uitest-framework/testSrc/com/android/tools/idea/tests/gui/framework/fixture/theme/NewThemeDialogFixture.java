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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.DialogWrapper;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class NewThemeDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static NewThemeDialogFixture findDialog(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "New Theme"));
    return new NewThemeDialogFixture(robot, dialog, getDialogWrapperFrom(dialog, DialogWrapper.class));
  }

  private NewThemeDialogFixture(@NotNull Robot robot,
                                @NotNull JDialog target,
                                @NotNull DialogWrapper dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  @NotNull
  public NewThemeDialogFixture setName(@NotNull String themeName) {
    JTextField textField = GuiTests.waitUntilShowing(robot(), this.target(), Matchers.byType(JTextField.class));
    new JTextComponentFixture(robot(), textField).deleteText().enterText(themeName);
    return this;
  }

  @NotNull
  public NewThemeDialogFixture setParentTheme(@NotNull String parentTheme) {
    JComboBox comboBox = GuiTests.waitUntilShowing(robot(), this.target(), Matchers.byType(JComboBox.class));
    new JComboBoxFixture(robot(), comboBox).selectItem(parentTheme);
    return this;
  }

  public void clickOk() {
    findAndClickOkButton(this);
    waitUntilNotShowing();
  }
}
