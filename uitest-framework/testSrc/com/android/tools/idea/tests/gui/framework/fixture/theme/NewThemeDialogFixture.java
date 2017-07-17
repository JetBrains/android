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
import com.intellij.openapi.util.Ref;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class NewThemeDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static NewThemeDialogFixture findDialog(@NotNull Robot robot) {
    Ref<DialogWrapper> wrapperRef = new Ref<>();
    JDialog dialog = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<JDialog>(JDialog.class){
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (!"New Theme".equals(dialog.getTitle())) {
          return false;
        }
        wrapperRef.set(getDialogWrapperFrom(dialog, DialogWrapper.class));
        return true;
      }
    });
    return new NewThemeDialogFixture(robot, dialog, wrapperRef.get());
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
