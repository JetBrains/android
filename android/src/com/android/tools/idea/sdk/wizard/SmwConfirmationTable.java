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
package com.android.tools.idea.sdk.wizard;

import com.android.annotations.NonNull;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/**
 * Select table in SMW confirmation step.
 * Source: PluginTable.
 */
public class SmwConfirmationTable extends JBTable {

  public SmwConfirmationTable() {
    super();
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);
    setStriped(true);
    setTableHeader(null);
  }

  public SmwConfirmationTable(@NonNull SmwConfirmationTableModel model) {
    super(model);
    setModel(model);
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    super.setModel(model);
    if (!(model instanceof SmwConfirmationTableModel)) {
      return;
    }
    SmwConfirmationTableModel tableModel = (SmwConfirmationTableModel) model;
    getColumnModel().setColumnMargin(0);
    for (int i = 0; i < tableModel.getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      final ColumnInfo columnInfo = tableModel.getColumnInfo(i);
      //noinspection unchecked
      column.setCellEditor(columnInfo.getEditor(null));
      if (columnInfo.getColumnClass() == Boolean.class) {
        TableUtil.setupCheckboxColumn(column);
      }
    }

    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);
    setStriped(true);
    setTableHeader(null);

    if (tableModel.getColumnCount() > 1) {
      setColumnWidth(1, new JCheckBox().getPreferredSize().width + 4);
    }
  }

  public void setColumnWidth(final int columnIndex, final int width) {
    TableColumn column = getColumnModel().getColumn(columnIndex);
    column.setMinWidth(width);
    column.setMaxWidth(width);
  }

  @Override
  public TableCellRenderer getCellRenderer(final int row, final int column) {
    SmwConfirmationTableModel model = (SmwConfirmationTableModel) getModel();
    final ColumnInfo columnInfo = model.getColumnInfo(column);
    //noinspection ConstantConditions,unchecked
    return columnInfo.getRenderer(model.getObjectAt(row));
  }

}
