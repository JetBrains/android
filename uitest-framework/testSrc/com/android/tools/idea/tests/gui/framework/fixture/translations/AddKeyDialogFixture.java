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
package com.android.tools.idea.tests.gui.framework.fixture.translations;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.theme.EditorTextFieldFixture;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class AddKeyDialogFixture {
  private final Robot myRobot;
  private final Container myTarget;

  AddKeyDialogFixture(@NotNull Robot robot, @NotNull Container target) {
    myRobot = robot;
    myTarget = target;
  }

  @NotNull
  public EditorTextFieldFixture getKeyTextField() {
    return EditorTextFieldFixture.findByName(myRobot, myTarget, "keyTextField");
  }

  @NotNull
  public EditorTextFieldFixture getDefaultValueTextField() {
    return EditorTextFieldFixture.findByName(myRobot, myTarget, "defaultValueTextField");
  }

  @NotNull
  public JComboBoxFixture getResourceFolderComboBox() {
    return new JComboBoxFixture(myRobot, "resourceFolderComboBox");
  }

  public void waitUntilErrorLabelFound(@NotNull String text) {
    text = "<html><font color='#" + ColorUtil.toHex(JBColor.RED) + "'><left>" + text + "</left><br/></font></html>";
    GuiTests.waitUntilFound(myRobot, myTarget, JLabelMatcher.withText(text));
  }

  @NotNull
  public JButtonFixture getOkButton() {
    return new JButtonFixture(myRobot, "okButton");
  }

  @NotNull
  public JButtonFixture getCancelButton() {
    return new JButtonFixture(myRobot, "cancelButton");
  }
}
