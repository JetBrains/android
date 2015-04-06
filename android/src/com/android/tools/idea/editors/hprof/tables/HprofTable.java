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
package com.android.tools.idea.editors.hprof.tables;

import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.table.DefaultTableCellHeaderRenderer;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.util.List;

public abstract class HprofTable extends JBTable {
  public HprofTable(@NotNull TableModel model) {
    super(model);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setRowHeight(19);
    setUpdateSelectionOnSort(true);
    setAutoCreateRowSorter(true);
    setDefaultSort();
    prettifyTable();
  }

  public void prettifyTable() {
    TableModel tableModel = getModel();
    assert (tableModel instanceof HprofTableModel);
    HprofTableModel hprofTableModel = (HprofTableModel)tableModel;

    for (int i = 0; i < getColumnModel().getColumnCount(); ++i) {
      javax.swing.table.TableColumn column = getColumnModel().getColumn(i);
      column.setPreferredWidth(hprofTableModel.getColumnWidth(i));

      DefaultTableCellHeaderRenderer headerRenderer = new DefaultTableCellHeaderRenderer();
      headerRenderer.setHorizontalAlignment(hprofTableModel.getColumnHeaderJustification(i));
      column.setHeaderRenderer(headerRenderer);
    }
  }

  public void notifyDominatorsComputed() {
    List<? extends RowSorter.SortKey> sortKeys = getSortOrder();
    ((HprofTableModel)getModel()).enableAllColumns();
    setSortOrder(sortKeys);
    prettifyTable();
  }

  public void setDefaultSort() {
    if (getColumnModel().getColumnCount() > 0) {
      getRowSorter().toggleSortOrder(0);
    }
  }

  public List<? extends RowSorter.SortKey> getSortOrder() {
    return getRowSorter().getSortKeys();
  }

  public void setSortOrder(@Nullable List<? extends RowSorter.SortKey> sortKeys) {
    if (sortKeys == null || sortKeys.isEmpty()) {
      setDefaultSort();
    }
    else {
      getRowSorter().setSortKeys(sortKeys);
    }
  }
}
