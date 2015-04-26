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

import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;

public abstract class HprofTreeTableModel extends ListTreeTableModelOnColumns {

  protected HprofTreeTableModel(@Nullable TreeNode root, @Nullable ColumnInfo[] columns) {
    super(root, columns);
  }

  public int getColumnWidth(int column) {
    return getColumn(column).getColumnWidth();
  }

  public int getColumnHeaderJustification(int column) {
    return getColumn(column).getHeaderJustification();
  }

  @Override
  public Object getValueAt(@NotNull Object node, int column) {
    //noinspection unchecked
    return isColumnEnabled(column) ? getColumn(column).valueOf(node) : null;
  }

  @Override
  public boolean isCellEditable(@NotNull Object node, int column) {
    return false;
  }

  @Override
  public void setValueAt(@Nullable Object aValue, @NotNull Object node, int column) {

  }

  public void enableAllColumns() {
    for (int i = 0; i < getColumnCount(); ++i) {
      getColumn(i).setEnabled(true);
    }
  }

  protected boolean isColumnEnabled(int column) {
    return getColumn(column).getEnabled();
  }

  protected abstract HprofColumnInfo getColumn(int column);
}
