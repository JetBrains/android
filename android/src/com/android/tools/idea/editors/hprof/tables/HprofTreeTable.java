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

import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import org.jetbrains.annotations.NotNull;
import sun.swing.table.DefaultTableCellHeaderRenderer;

import javax.swing.*;

public class HprofTreeTable extends TreeTableView {
  public HprofTreeTable(@NotNull ListTreeTableModelOnColumns columns) {
    super(columns);
    setDefaultSettings();
  }

  public void prettifyTable() {
    TreeTableModel treeTableModel = getTableModel();
    assert (treeTableModel instanceof HprofTreeTableModel);
    HprofTreeTableModel hprofTreeTableModel = (HprofTreeTableModel)treeTableModel;

    for (int i = 0; i < getColumnModel().getColumnCount(); ++i) {
      javax.swing.table.TableColumn column = getColumnModel().getColumn(i);
      column.setPreferredWidth(hprofTreeTableModel.getColumnWidth(i));

      DefaultTableCellHeaderRenderer headerRenderer = new DefaultTableCellHeaderRenderer();
      //noinspection MagicConstant
      headerRenderer.setHorizontalAlignment(hprofTreeTableModel.getColumnHeaderJustification(i));
      column.setHeaderRenderer(headerRenderer);
    }
  }

  public void notifyDominatorsComputed() {
    ((HprofTreeTableModel)getTableModel()).enableAllColumns();
    setTableModel(getTableModel());
    prettifyTable();
  }

  private void setDefaultSettings() {
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    assert (getTree() != null);
    getTree().setLargeModel(true);
    prettifyTable();
  }
}
