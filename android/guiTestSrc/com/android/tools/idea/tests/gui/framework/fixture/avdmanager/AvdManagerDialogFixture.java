/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.avdmanager;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Controls the Avd Manager Dialog for GUI test cases
 */
public class AvdManagerDialogFixture extends ComponentFixture<JFrame> {

  public AvdManagerDialogFixture(@NotNull Robot robot, @NotNull JFrame target) {
    super(robot, target);
  }

  @NotNull
  public static AvdManagerDialogFixture find(@NotNull Robot robot) {
    JFrame frame = GuiTests.waitUntilFound(robot, new GenericTypeMatcher<JFrame>(JFrame.class) {
      @Override
      protected boolean isMatching(JFrame dialog) {
        return "Android Virtual Device Manager".equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
    return new AvdManagerDialogFixture(robot, frame);
  }

  public AvdEditWizardFixture createNew() {
    JButton newAvdButton = findButtonByText("Create Virtual Device...");
    robot.click(newAvdButton);
    return AvdEditWizardFixture.find(robot);
  }

  public AvdEditWizardFixture editAvdWithName(@NotNull String name) {
    final TableView tableView = robot.finder().findByType(target, TableView.class, true);
    JTableFixture tableFixture = new JTableFixture(robot, tableView);
    TableCell cell = tableFixture.cell(name);
    final TableCell actionCell = TableCell.row(cell.row).column(7);

    JTableCellFixture actionCellFixture = tableFixture.cell(actionCell);

    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        tableView.editCellAt(actionCell.row, actionCell.column);
      }
    });

    JPanel actionPanel = (JPanel)actionCellFixture.editor();
    JBLabel editButtonLabel = robot.finder().find(actionPanel, new GenericTypeMatcher<JBLabel>(JBLabel.class) {
      @Override
      protected boolean isMatching(JBLabel component) {
        return "Edit this AVD".equals(component.getToolTipText());
      }
    });
    robot.click(editButtonLabel);
    return AvdEditWizardFixture.find(robot);
  }

  @NotNull
  private JButton findButtonByText(@NotNull String text) {
    return robot.finder().find(target, JButtonMatcher.withText(text).andShowing());
  }

  public void selectAvdByName(@NotNull String name) {
    TableView tableView = robot.finder().findByType(target, TableView.class, true);
    JTableFixture tableFixture = new JTableFixture(robot, tableView);

    TableCell cell = tableFixture.cell(name);
    tableFixture.selectCell(cell);
  }

  public void deleteAvdByName(String name) {
    TableView tableView = robot.finder().findByType(target, TableView.class, true);
    JTableFixture tableFixture = new JTableFixture(robot, tableView);

    TableCell cell = tableFixture.cell(name);
    tableFixture.click(cell, MouseButton.RIGHT_BUTTON);

    JPopupMenu contextMenu = robot.findActivePopupMenu();
    JPopupMenuFixture contextMenuFixture = new JPopupMenuFixture(robot, contextMenu);
    contextMenuFixture.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(JMenuItem component) {
        return "Delete".equals(component.getText());
      }
    }).click();

    JOptionPaneFixture optionPaneFixture = new JOptionPaneFixture(robot);
    optionPaneFixture.yesButton().click();
  }

  public void close() {
    robot.close(target);
  }
}
