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
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.EditorTextFieldFixture;
import com.android.tools.idea.uibuilder.actions.MorphDialog;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JButtonFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MorphDialogFixture extends ComponentFixture<MorphDialogFixture, MorphDialog> {

  public MorphDialogFixture(@NotNull Robot robot) {
    super(MorphDialogFixture.class, robot, MorphDialog.MORPH_DIALOG_NAME, MorphDialog.class);
  }

  @NotNull
  public EditorTextFieldFixture getTextField() {
    return EditorTextFieldFixture.find(robot(), target());
  }

  public JButtonFixture getOkButton() {
    return new JButtonFixture(robot(), (JButton)robot().finder().find(
      target(),
      c -> c instanceof JButton
           && "Apply".equals(((JButton)c).getText())));
  }
}