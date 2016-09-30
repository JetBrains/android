/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.gfxtrace;

import com.google.common.base.Verify;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentPreconditions;
import org.fest.swing.driver.JTreeDriver;
import org.fest.swing.driver.JTreeLocation;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.util.Preconditions;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;

public class ScrollAwareJTreeDriver extends JTreeDriver {

  private final JTreeLocation location = new JTreeLocation();

  public ScrollAwareJTreeDriver(@Nonnull Robot robot) {
    super(robot);
  }

  @RunsInEDT
  @Override
  public void selectRow(@Nonnull JTree tree, int row) {
    Rectangle info = scrollToRow(tree, row);
    if (!(tree.getSelectionCount() == 1 && tree.isRowSelected(row))) {
      Point p = new Point(info.x + 5, getMiddleOfRow(info));
      robot.click(tree, p); // path not selected, click to select
      Verify.verify(tree.getSelectionCount() == 1, "wrong number of items selected " + tree.getSelectionCount() + " click fail " + p + " in " + info + " in " + tree.getVisibleRect());
      Verify.verify(tree.isRowSelected(row), "wrong row selected " + tree.getLeadSelectionRow() + " and not " + row + " click fail " + p + " in " + info + " in " + tree.getVisibleRect());
    }
  }

  @RunsInEDT
  @Override
  public void expandRow(@Nonnull JTree tree, int row) {
    Rectangle info = scrollToRow(tree, row);
    robot.waitForIdle();
    if (!tree.isExpanded(row)) {
      int rightChildIndent = (Integer)UIManager.get("Tree.rightChildIndent");
      // from BasicTreeUI.paintExpandControl
      Point p = new Point(info.x - rightChildIndent + 1, getMiddleOfRow(info));
      robot.click(tree, p);
      Verify.verify(tree.isExpanded(row), "click fail " + p + " in " + info + " in " + tree.getVisibleRect());
    }
  }

  @RunsInEDT
  @Override
  public void rightClickRow(@Nonnull JTree tree, int row) {
    Rectangle info = scrollToRow(tree, row);
    Point p = new Point(info.x + 5, getMiddleOfRow(info));
    robot.click(tree, p, MouseButton.RIGHT_BUTTON, 1);
  }

  @RunsInEDT
  @Override
  public @Nonnull JPopupMenu showPopupMenu(@Nonnull JTree tree, int row) {
    Rectangle info = scrollToRow(tree, row);
    Point p = new Point(info.x + 5, getMiddleOfRow(info));
    return robot.showPopupMenu(tree, p);
  }

  @RunsInEDT
  private @Nonnull Rectangle scrollToRow(final @Nonnull JTree tree, final int row) {
    Rectangle result = GuiActionRunner.execute(new GuiQuery<Rectangle>() {
      @Override
      protected Rectangle executeInEDT() {
        ComponentPreconditions.checkEnabledAndShowing(tree);
        Rectangle bounds = location.rowBoundsAndCoordinates(tree, row).first;
        Rectangle returnValue = new Rectangle(bounds);
        int rightChildIndent = (Integer)UIManager.get("Tree.rightChildIndent");
        Icon expandedIcon = (Icon)UIManager.get("Tree.expandedIcon");
        // from BasicTreeUI.paintExpandControl
        bounds.translate(-rightChildIndent + 1 -expandedIcon.getIconWidth() / 2, 0);
        Rectangle visibleRect = tree.getVisibleRect();
        if (bounds.width > visibleRect.width) {
          bounds.width = visibleRect.width;
        }
        tree.scrollRectToVisible(bounds);
        return returnValue;
      }
    });
    return Preconditions.checkNotNull(result);
  }

  private int getMiddleOfRow(Rectangle bounds) {
    int scrollBarSize = (Integer)UIManager.get("ScrollBar.width");
    return bounds.y + Math.min(bounds.height / 2, bounds.height - scrollBarSize);
  }
}
