/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.DialogWrapper;
import javax.swing.JDialog;
import javax.swing.JTextField;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

public class ColorPickerDialogFixture extends IdeaDialogFixture<DialogWrapper> {

  @NotNull
  public static ColorPickerDialogFixture find(@NotNull Robot robot) {
    return new ColorPickerDialogFixture(
      robot, IdeaDialogFixture.find(robot, DialogWrapper.class, Matchers.byTitle(JDialog.class, "Select Color")));
  }

  private ColorPickerDialogFixture(@NotNull Robot robot,
                                     @NotNull DialogAndWrapper<DialogWrapper> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  public ColorPickerDialogFixture setHexColor(@NotNull String hexColor) {
    JTextField hexField = GuiTests.waitUntilShowing(robot(), target(), new GenericTypeMatcher<JTextField>(JTextField.class) {
      @Override
      protected boolean isMatching(@NotNull JTextField component) {
        return component.getDocument().getLength() == 6;
      }
    });
    new JTextComponentFixture(robot(), hexField).enterText(hexColor);
    return this;
  }

  public void clickChoose() {
    GuiTests.findAndClickButton(this, "Choose");
  }
}
