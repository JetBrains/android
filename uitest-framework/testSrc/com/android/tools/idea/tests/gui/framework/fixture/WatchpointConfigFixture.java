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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.cell.JComboBoxCellReader;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;

public class WatchpointConfigFixture {
  final Robot robot;
  private final JPanel myJPanel;

  public WatchpointConfigFixture(Robot robot, JPanel jPanel) {
    this.robot = robot;
    myJPanel = jPanel;
  }

  @NotNull
  public WatchpointConfigFixture selectAccessType(@NotNull String accessType) {
    final int EXPECTED_ITEM_COUNT = 3; // 3: Read, Write, Any
    Collection<JComboBox> allFound = robot.finder().findAll(myJPanel, Matchers.byType(JComboBox.class));
    JComboBox accessTypeComboBox = null;
    for (JComboBox comboBox : allFound) {
      if (comboBox.getItemCount() == EXPECTED_ITEM_COUNT) {
        String displayName = comboBox.getSelectedItem().toString();
        if (displayName.equals("Write")) {
          accessTypeComboBox = comboBox;
          break;
        }
      }
    }
    assertThat(accessTypeComboBox).isNotNull();

    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(robot, accessTypeComboBox);
    comboBoxFixture.replaceCellReader(DEBUGGER_PICKER_READER);
    comboBoxFixture.selectItem(accessType);
    return this;
  }

  public void clickDone() {
    Component doneButton = GuiTests.waitUntilShowingAndEnabled(robot, myJPanel, Matchers.byText(JButton.class, "Done"));
    robot.click(doneButton);
  }

  private static final JComboBoxCellReader DEBUGGER_PICKER_READER =
      (jComboBox, index) -> (GuiQuery.getNonNull(() -> jComboBox.getItemAt(index).toString()));
}
