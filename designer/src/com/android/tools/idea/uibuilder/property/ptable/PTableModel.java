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

import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.List;

public class PTableModel extends AbstractTableModel {
  private List<PTableItem> myItems;

  public PTableModel() {
    myItems = Collections.emptyList();
  }

  public void setItems(@NotNull List<PTableItem> items) {
    myItems = items;
    fireTableDataChanged();
  }

  public void insertRow(int row, @NotNull PTableItem item) {
    myItems.add(row, item);
    fireTableRowsInserted(row, row);
  }

  public void deleteRow(int row) {
    myItems.remove(row);
    fireTableRowsDeleted(row, row);
  }

  @Override
  public int getRowCount() {
    return myItems.size();
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public Object getValueAt(int row, int column) {
    return myItems.get(row);
  }

  @Override
  public void setValueAt(Object value, int row, int col) {
    myItems.get(row).setValue(value);
    fireTableCellUpdated(row, col);
  }

  @Override
  public boolean isCellEditable(int row, int col) {
    return col == 1 && myItems.get(row).isEditable(col);
  }

  // TODO: if we want to support multiple levels of hierarchy, then this should
  // be updated to collapse children recursively
  public void collapse(int row) {
    if (row >= myItems.size()) {
      return;
    }

    PTableItem item = myItems.get(row);
    if (item.hasChildren() && item.isExpanded()) {
      item.setExpanded(false);
      List<PTableItem> children = item.getChildren();
      for (int i = 0; i < children.size(); i++) {
        myItems.remove(row + 1);
      }
    }
    fireTableDataChanged();
  }

  public int getParent(int row) {
    if (row >= myItems.size()) {
      return row;
    }

    PTableItem item = myItems.get(row);
    if (item.getParent() == null) {
      return row;
    }

    PTableItem parent = item.getParent();
    do {
      row--;
    }
    while (row >= 0 && myItems.get(row) != parent);
    return row;
  }

  public void expand(int row) {
    if (row >= myItems.size()) {
      return;
    }

    PTableItem item = myItems.get(row);
    if (item.hasChildren() && !item.isExpanded()) {
      item.setExpanded(true);
      List<PTableItem> children = item.getChildren();
      for (int i = 0; i < children.size(); i++) {
        myItems.add(row + 1 + i, children.get(i));
      }
    }
    fireTableDataChanged();
  }
}
