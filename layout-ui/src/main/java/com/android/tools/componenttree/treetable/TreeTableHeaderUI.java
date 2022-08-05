/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.componenttree.treetable;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.jetbrains.annotations.NotNull;

/**
 * A [TableHeaderUI] that is skipping the paint of the column currently being edited.
 * Such a column will draw itself.
 */
public class TreeTableHeaderUI extends BasicTableHeaderUI {
  private TreeTableHeader treeTableHeader;

  public void installUI(JComponent c) {
    super.installUI(c);
    treeTableHeader = (TreeTableHeader)c;
  }

  // This is identical to BasicTableHeaderUI.paint
  // We override this here because paintCell is private in BasicTableHeaderUI.
  public void paint(@NotNull Graphics g, @NotNull JComponent c) {
    if (header.getColumnModel().getColumnCount() <= 0) {
      return;
    }
    boolean ltr = header.getComponentOrientation().isLeftToRight();

    Rectangle clip = g.getClipBounds();
    Point left = clip.getLocation();
    Point right = new Point( clip.x + clip.width - 1, clip.y );
    TableColumnModel cm = header.getColumnModel();
    int cMin = header.columnAtPoint( ltr ? left : right );
    int cMax = header.columnAtPoint( ltr ? right : left );
    // This should never happen.
    if (cMin == -1) {
      cMin =  0;
    }
    // If the table does not have enough columns to fill the view we'll get -1.
    // Replace this with the index of the last column.
    if (cMax == -1) {
      cMax = cm.getColumnCount()-1;
    }

    TableColumn draggedColumn = header.getDraggedColumn();
    int columnWidth;
    Rectangle cellRect = header.getHeaderRect(ltr ? cMin : cMax);
    TableColumn aColumn;
    if (ltr) {
      for(int column = cMin; column <= cMax ; column++) {
        aColumn = cm.getColumn(column);
        columnWidth = aColumn.getWidth();
        cellRect.width = columnWidth;
        if (aColumn != draggedColumn) {
          paintCell(g, cellRect, column);
        }
        cellRect.x += columnWidth;
      }
    } else {
      for(int column = cMax; column >= cMin; column--) {
        aColumn = cm.getColumn(column);
        columnWidth = aColumn.getWidth();
        cellRect.width = columnWidth;
        if (aColumn != draggedColumn) {
          paintCell(g, cellRect, column);
        }
        cellRect.x += columnWidth;
      }
    }

    // Paint the dragged column if we are dragging.
    if (draggedColumn != null) {
      int draggedColumnIndex = viewIndexForColumn(draggedColumn);
      Rectangle draggedCellRect = header.getHeaderRect(draggedColumnIndex);

      // Draw a gray well in place of the moving column.
      g.setColor(header.getParent().getBackground());
      g.fillRect(draggedCellRect.x, draggedCellRect.y,
                 draggedCellRect.width, draggedCellRect.height);

      draggedCellRect.x += header.getDraggedDistance();

      // Fill the background.
      g.setColor(header.getBackground());
      g.fillRect(draggedCellRect.x, draggedCellRect.y,
                 draggedCellRect.width, draggedCellRect.height);

      paintCell(g, draggedCellRect, draggedColumnIndex);
    }

    // Remove all components in the rendererPane.
    rendererPane.removeAll();
  }

  private Component getHeaderRenderer(int columnIndex) {
    TableColumn aColumn = header.getColumnModel().getColumn(columnIndex);
    TableCellRenderer renderer = aColumn.getHeaderRenderer();
    if (renderer == null) {
      renderer = header.getDefaultRenderer();
    }

    return renderer.getTableCellRendererComponent(header.getTable(),
                                                  aColumn.getHeaderValue(),
                                                  false, false,
                                                  -1, columnIndex);
  }

  private void paintCell(Graphics g, Rectangle cellRect, int columnIndex) {
    if (treeTableHeader.getEditingColumn() == columnIndex) {
      Component component = treeTableHeader.getComponent(0);
      component.setBounds(cellRect);
      component.validate();
    }
    else {
      Component component = getHeaderRenderer(columnIndex);
      rendererPane.paintComponent(g, component, header, cellRect.x, cellRect.y,
                                  cellRect.width, cellRect.height, true);
    }
  }

  private int viewIndexForColumn(TableColumn aColumn) {
    TableColumnModel cm = header.getColumnModel();
    for (int column = 0; column < cm.getColumnCount(); column++) {
      if (cm.getColumn(column) == aColumn) {
        return column;
      }
    }
    return -1;
  }
}
