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
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.table.TableView;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.driver.JTableLocation;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.event.WindowEvent;

import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;

/**
 * Controls the Avd Manager Dialog for GUI test cases
 */
public class AvdManagerDialogFixture extends ComponentFixture<AvdManagerDialogFixture, JFrame> {

  private final IdeFrameFixture myIdeFrame;

  public AvdManagerDialogFixture(@NotNull Robot robot, @NotNull JFrame target, @NotNull IdeFrameFixture ideFrame) {
    super(AvdManagerDialogFixture.class, robot, target);
    myIdeFrame = ideFrame;
  }

  @NotNull
  public static AvdManagerDialogFixture find(@NotNull Robot robot, @NotNull IdeFrameFixture ideFrame) {
    JFrame frame = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<JFrame>(JFrame.class) {
      @Override
      protected boolean isMatching(@NotNull JFrame dialog) {
        // Waiting for the dialog to be active allows the "dialog grow animation" to end
        return dialog.isActive() && "Android Virtual Device Manager".equals(dialog.getTitle());
      }
    });
    return new AvdManagerDialogFixture(robot, frame, ideFrame);
  }

  public AvdEditWizardFixture createNew() {
    JButton newAvdButton = GuiTests.waitUntilShowingAndEnabled(robot(), target(), JButtonMatcher.withText("Create Virtual Device..."));
    new JButtonFixture(robot(), newAvdButton).click();
    return AvdEditWizardFixture.find(robot());
  }

  @NotNull
  public AvdEditWizardFixture editAvdWithName(@NotNull String name) {
    JPanel actionPanel = getAvdActionCell(name);
    HyperlinkLabel editButtonLabel = robot().finder().find(actionPanel, Matchers.byTooltip(HyperlinkLabel.class, "Edit this AVD"));
    robot().click(editButtonLabel);
    return AvdEditWizardFixture.find(robot());
  }

  @NotNull
  public AvdManagerDialogFixture startAvdWithName(@NotNull String name) {
    JPanel actionPanel = getAvdActionCell(name);
    HyperlinkLabel startButtonLabel = robot().finder().find(actionPanel, Matchers.byTooltip(HyperlinkLabel.class, "Launch this AVD in the emulator"));
    robot().click(startButtonLabel);
    return this;
  }

  @NotNull
  private JPanel getAvdActionCell(@NotNull String name) {
    TableView tableView = robot().finder().findByType(target(), TableView.class, true);
    JTableFixture tableFixture = new JTableFixture(robot(), tableView);
    JTableCellFixture cell = tableFixture.cell(name);
    TableCell actionCell = TableCell.row(cell.row()).column(8);

    JTableCellFixture actionCellFixture = tableFixture.cell(actionCell);

    GuiTask.execute(() -> tableView.editCellAt(actionCell.row, actionCell.column));

    // Hack: sometimes the action hyperlinks in the action cell do not return true
    // when isShowing() is called, even when the action links are clearly visible.
    // This rare bug prevents the Robot from finding and clicking on the element.
    // A weird quirk is that the action cell's items will begin returning true for
    // isShowing() when the mouse cursor is moved over the action cell.
    // See b/63768559 for a similar bug
    robot().moveMouse(
      tableView,
      new JTableLocation().pointAt(tableView, actionCell.row, actionCell.column));

    return (JPanel)actionCellFixture.editor();
  }

  @NotNull
  public AvdManagerDialogFixture selectAvd(@NotNull String name) {
    TableView tableView = robot().finder().findByType(target(), TableView.class, true);
    JTableFixture tableFixture = new JTableFixture(robot(), tableView);

    tableFixture.cell(name).select();
    return this;
  }

  @NotNull
  public AvdManagerDialogFixture deleteAvd(@NotNull String name) {
    TableView tableView = robot().finder().findByType(target(), TableView.class, true);
    JTableFixture tableFixture = new JTableFixture(robot(), tableView);

    JTableCellFixture cell = tableFixture.cell(name);
    cell.click(RIGHT_BUTTON);

    JPopupMenuFixture contextMenuFixture = new JPopupMenuFixture(robot(), robot().findActivePopupMenu());
    contextMenuFixture.menuItemWithPath("Delete").click();

    MessagesFixture.findByTitle(robot(), "Confirm Deletion").clickYes();
    return this;
  }

  public AvdManagerDialogFixture stopAvd(String name) {
    new JTableFixture(robot(), robot().finder().findByType(target(), TableView.class, true)).cell(name).click(RIGHT_BUTTON);
    new JPopupMenuFixture(robot(), robot().findActivePopupMenu()).menuItemWithPath("Stop").click();
    return this;
  }

  public void close() {
    // HACK: There is no button to close the dialog (eg a "Cancel)
    // On the build bot the window manager doesn't allow "click to close";
    // ESC is not reliable
    // robot().close(target()) sends a WINDOW_CLOSING event, but it may be intercepted by other Components
    // We send WINDOW_CLOSING directly to the Windows JFrame
    GuiTask.execute(() -> target().dispatchEvent(new WindowEvent(target(), WindowEvent.WINDOW_CLOSING)));
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
    myIdeFrame.requestFocusIfLost();
  }
}
