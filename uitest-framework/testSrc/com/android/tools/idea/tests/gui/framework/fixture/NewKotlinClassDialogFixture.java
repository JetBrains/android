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

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.util.Trinity;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JTextField;
import org.fest.swing.cell.JComboBoxCellReader;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

public class NewKotlinClassDialogFixture implements ContainerFixture<JDialog> {

  public static NewKotlinClassDialogFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "New Kotlin File/Class"));
    return new NewKotlinClassDialogFixture(ideFrameFixture, dialog);
  }

  private final IdeFrameFixture myIdeFrameFixture;
  private final JDialog myDialog;

  private NewKotlinClassDialogFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
  }

  @NotNull
  public NewKotlinClassDialogFixture enterName(@NotNull String name) {
    JTextField textField = robot().finder().findByLabel(target(), "Name:", JTextField.class, true);

    new JTextComponentFixture(robot(), textField).deleteText();

    // Manually type into the text field. Workaround for Linux clipboard bug.
    // There does not seem to be a reliable way to wait for the clipboard
    // to actually contain our value if we use the clipboard multiple times
    // in a test. StudioRobot.enableXwinClipboardWorkaround seems to only make
    // tests that enter text through pasting reliable only once.
    robot().focusAndWaitForFocusGain(textField);
    robot().typeText(name);

    return this;
  }

  private static final JComboBoxCellReader KIND_PICKER_READER = (jComboBox, index) -> {
    Trinity element = (Trinity) (jComboBox.getModel().getElementAt(index));
    return element.getFirst().toString();
  };

  @NotNull
  public NewKotlinClassDialogFixture selectType(@NotNull String type) {
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(robot(), robot().finder().findByType(target(), JComboBox.class));
    comboBoxFixture.replaceCellReader(KIND_PICKER_READER);
    comboBoxFixture.selectItem(type);
    return this;
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }

  @NotNull
  @Override
  public JDialog target() {
    return myDialog;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myIdeFrameFixture.robot();
  }
}
