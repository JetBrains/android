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

import javax.swing.table.AbstractTableModel;

public abstract class HprofTableModel extends AbstractTableModel {
  @Override
  public String getColumnName(int column) {
    return getColumn(column).getColumnName();
  }

  @Override
  public Class<?> getColumnClass(int column) {
    return getColumn(column).getColumnClass();
  }

  public int getColumnWidth(int column) {
    return getColumn(column).getColumnWidth();
  }

  public int getColumnHeaderJustification(int column) {
    return getColumn(column).getHeaderJustification();
  }

  @Override
  public Object getValueAt(int row, int column) {
    //noinspection unchecked
    return isColumnEnabled(column) ? getColumn(column).getValue(this, row) : null;
  }

  public abstract void enableAllColumns();

  protected abstract boolean isColumnEnabled(int unmappedIndex);

  protected abstract TableColumn getColumn(int uiIndex);
}
