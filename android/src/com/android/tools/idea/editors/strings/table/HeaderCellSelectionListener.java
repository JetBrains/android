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
package com.android.tools.idea.editors.strings.table;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HeaderCellSelectionListener extends MouseAdapter {
  private final JTable myTable;
  private int myUserResizeIndex;

  public HeaderCellSelectionListener(@NotNull JTable table) {
    myTable = table;
    myUserResizeIndex = -1;
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    // Column widths may have changed, so resize the table
    ColumnUtil.expandToViewportWidthIfNecessary(myTable, myUserResizeIndex);
    myUserResizeIndex = -1;
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    int index = myTable.columnAtPoint(e.getPoint());
    TableColumn column = myTable.getColumnModel().getColumn(index);
    HeaderCellRenderer renderer = (HeaderCellRenderer) column.getHeaderRenderer();

    // Collapse or expand the column depending on whether it is at least at its minimum expanded width
    boolean useCollapsedWidth = column.getWidth() >= renderer.getMinimumExpandedWidth();
    ColumnUtil.setPreferredWidth(column, useCollapsedWidth ? renderer.getCollapsedWidth() : renderer.getFullExpandedWidth());

    // Collapsing a column may have made the table not wide enough
    ColumnUtil.expandToViewportWidthIfNecessary(myTable, index);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    TableColumn column = myTable.getTableHeader().getResizingColumn();
    if (column != null) {
      myUserResizeIndex = column.getModelIndex();
      ColumnUtil.setPreferredWidth(column, column.getWidth());
    }
  }
}
