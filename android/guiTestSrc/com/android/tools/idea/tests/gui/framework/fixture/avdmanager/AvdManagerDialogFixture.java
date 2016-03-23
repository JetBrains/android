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
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.table.TableView;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

/**
 * Controls the Avd Manager Dialog for GUI test cases
 */
public class AvdManagerDialogFixture extends ComponentFixture<AvdManagerDialogFixture, JFrame> {

  public AvdManagerDialogFixture(@NotNull Robot robot, @NotNull JFrame target) {
    super(AvdManagerDialogFixture.class, robot, target);
  }

  @NotNull
  public static AvdManagerDialogFixture find(@NotNull Robot robot) {
    JFrame frame = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<JFrame>(JFrame.class) {
      @Override
      protected boolean isMatching(@NotNull JFrame dialog) {
        return "Android Virtual Device Manager".equals(dialog.getTitle());
      }
    });
    return new AvdManagerDialogFixture(robot, frame);
  }

  public AvdEditWizardFixture createNew() {
    JButton newAvdButton = findButtonByText("Create Virtual Device...");
    final JButtonFixture button = new JButtonFixture(robot(), newAvdButton);
    button.requireEnabled();
    button.requireVisible();
    button.click();
    return AvdEditWizardFixture.find(robot());
  }

  public AvdEditWizardFixture editAvdWithName(@NotNull String name) {
    final TableView tableView = robot().finder().findByType(target(), TableView.class, true);
    JTableFixture tableFixture = new JTableFixture(robot(), tableView);
    JTableCellFixture cell = tableFixture.cell(name);
    final TableCell actionCell = TableCell.row(cell.row()).column(7);

    JTableCellFixture actionCellFixture = tableFixture.cell(actionCell);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        tableView.editCellAt(actionCell.row, actionCell.column);
      }
    });

    JPanel actionPanel = (JPanel)actionCellFixture.editor();
    HyperlinkLabel editButtonLabel = robot().finder().find(actionPanel, new GenericTypeMatcher<HyperlinkLabel>(HyperlinkLabel.class) {
      @Override
      protected boolean isMatching(@NotNull HyperlinkLabel component) {
        return "Edit this AVD".equals(component.getToolTipText());
      }
    });
    robot().click(editButtonLabel);
    return AvdEditWizardFixture.find(robot());
  }

  @NotNull
  private JButton findButtonByText(@NotNull String text) {
    return robot().finder().find(target(), JButtonMatcher.withText(text).andShowing());
  }

  public void selectAvdByName(@NotNull String name) {
    TableView tableView = robot().finder().findByType(target(), TableView.class, true);
    JTableFixture tableFixture = new JTableFixture(robot(), tableView);

    tableFixture.cell(name).select();
  }

  public void deleteAvdByName(String name) {
    TableView tableView = robot().finder().findByType(target(), TableView.class, true);
    JTableFixture tableFixture = new JTableFixture(robot(), tableView);

    JTableCellFixture cell = tableFixture.cell(name);
    cell.click(RIGHT_BUTTON);

    JPopupMenu contextMenu = robot().findActivePopupMenu();
    assertNotNull(contextMenu);
    JPopupMenuFixture contextMenuFixture = new JPopupMenuFixture(robot(), contextMenu);
    contextMenuFixture.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem component) {
        return "Delete".equals(component.getText());
      }
    }).click();

    MessagesFixture.findByTitle(robot(), target(), "Confirm Deletion").clickYes();
  }

  public void close() {
    robot().close(target());
  }
}
