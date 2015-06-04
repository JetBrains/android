/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ptable;

import com.android.tools.idea.uibuilder.property.ptable.renderers.PNameRenderer;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PTable extends JBTable {
  private final PNameRenderer myNameRenderer = new PNameRenderer();
  private PTableModel myModel;

  private int myMouseHoverRow;
  private int myMouseHoverCol;

  public PTable(@NotNull PTableModel model) {
    super(model);
    myModel = model;

    // since the row heights are uniform, there is no need to look at more than a few items
    setMaxItemsForSizeCalculation(5);

    // When a label cannot be fully displayed, hovering over it results in a popup that extends beyond the
    // cell bounds to show the full value. We don't need this feature as it'll end up covering parts of the
    // cell we don't want covered.
    setExpandableItemsEnabled(false);

    setShowColumns(false);
    setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    setShowVerticalLines(true);
    setIntercellSpacing(new Dimension(0, 1));
    setGridColor(UIUtil.getSlightlyDarkerColor(getBackground()));

    setColumnSelectionAllowed(false);
    setCellSelectionEnabled(false);
    setRowSelectionAllowed(true);

    addMouseListener(new MouseTableListener());

    HoverListener hoverListener = new HoverListener();
    addMouseMotionListener(hoverListener);
    addMouseListener(hoverListener);
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    myModel = (PTableModel)model;
    super.setModel(model);
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    if (column == 0) {
      return myNameRenderer;
    }

    PTableItem value = (PTableItem)getValueAt(row, column);
    return value.getCellRenderer();
  }

  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    PTableItem value = (PTableItem)getValueAt(row, column);
    return value.getCellEditor();
  }

  public boolean isHover(int row, int col) {
    return row == myMouseHoverRow && col == myMouseHoverCol;
  }

  // Expand/Collapse group items if necessary
  private class MouseTableListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      int row = rowAtPoint(e.getPoint());
      if (row == -1) {
        return;
      }

      PTableItem item = (PTableItem)myModel.getValueAt(row, 0);
      if (!item.hasChildren()) {
        return;
      }

      Rectangle rect = getCellRect(row, convertColumnIndexToView(0), false);
      if (!rect.contains(e.getX(), e.getY())) {
        return;
      }
      if (!PNameRenderer.hitTestTreeNodeIcon(item, e.getX() - rect.x)) {
        return;
      }

      if (item.isExpanded()) {
        myModel.collapse(row);
      }
      else {
        myModel.expand(row);
      }
    }
  }

  // Repaint cells on mouse hover
  private class HoverListener extends MouseAdapter {
    private int myPreviousHoverRow = -1;
    private int myPreviousHoverCol = -1;

    @Override
    public void mouseMoved(MouseEvent e) {
      myMouseHoverRow = rowAtPoint(e.getPoint());
      if (myMouseHoverRow >= 0) {
        myMouseHoverCol = columnAtPoint(e.getPoint());
      }

      // remove hover from the previous cell
      if (myPreviousHoverRow != -1 && (myPreviousHoverRow != myMouseHoverRow || myPreviousHoverCol != myMouseHoverCol)) {
        repaint(getCellRect(myPreviousHoverRow, myPreviousHoverCol, true));
        myPreviousHoverRow = -1;
      }

      if (myMouseHoverCol < 0) {
        return;
      }

      // repaint cell that has the hover
      repaint(getCellRect(myMouseHoverRow, myMouseHoverCol, true));

      myPreviousHoverRow = myMouseHoverRow;
      myPreviousHoverCol = myMouseHoverCol;
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (myMouseHoverRow != -1 && myMouseHoverCol != -1) {
        Rectangle cellRect = getCellRect(myMouseHoverRow, 1, true);
        myMouseHoverRow = myMouseHoverCol = -1;
        repaint(cellRect);
      }
    }
  }
}
