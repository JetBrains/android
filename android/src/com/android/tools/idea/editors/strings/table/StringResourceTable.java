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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.editors.strings.StringResourceData;
import com.android.tools.idea.rendering.Locale;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public final class StringResourceTable extends FrozenColumnTable {
  private final TableCellRenderer myLocaleRenderer;

  @Nullable
  private StringResourceTableColumnFilter myColumnFilter;

  private boolean myColumnPreferredWidthsSet;

  public StringResourceTable() {
    super(new StringResourceTableModel(), 4);

    setDefaultEditor(String.class, new StringTableCellEditor());
    setDefaultRenderer(String.class, new StringsCellRenderer());
    setRowSorter(new ThreeStateTableRowSorter<>(getModel()));

    putInInputMap(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete");
    putInInputMap(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");

    myLocaleRenderer = new LocaleRenderer(getDefaultTableHeaderRenderer());
  }

  @Nullable
  public StringResourceData getData() {
    return getModel().getData();
  }

  @Nullable
  public StringResourceTableRowFilter getRowFilter() {
    return (StringResourceTableRowFilter)getRowSorter().getRowFilter();
  }

  public void setRowFilter(@Nullable StringResourceTableRowFilter filter) {
    getRowSorter().setRowFilter(filter);
  }

  @Nullable
  public StringResourceTableColumnFilter getColumnFilter() {
    return myColumnFilter;
  }

  public void setColumnFilter(@Nullable StringResourceTableColumnFilter filter) {
    myColumnFilter = filter;
    createDefaultColumnsFromModel();
  }

  @Override
  boolean includeColumn(int modelColumnIndex) {
    if (modelColumnIndex < StringResourceTableModel.FIXED_COLUMN_COUNT) {
      return true;
    }

    if (myColumnFilter == null) {
      return true;
    }

    Locale locale = getModel().getLocale(modelColumnIndex);
    assert locale != null;

    return myColumnFilter.include(locale);
  }

  @NotNull
  @Override
  TableColumn createColumn(int modelColumnIndex) {
    TableColumn column = new TableColumn(modelColumnIndex);

    if (modelColumnIndex >= StringResourceTableModel.FIXED_COLUMN_COUNT) {
      column.setHeaderRenderer(myLocaleRenderer);
    }

    return column;
  }

  @NotNull
  @Override
  public TableRowSorter<StringResourceTableModel> getRowSorter() {
    //noinspection unchecked
    return (TableRowSorter<StringResourceTableModel>)super.getRowSorter();
  }

  @NotNull
  @Override
  public StringResourceTableModel getModel() {
    return (StringResourceTableModel)super.getModel();
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    super.setModel(model);
    getRowSorter().setModel((StringResourceTableModel)model);

    if (myColumnPreferredWidthsSet) {
      return;
    }

    IntStream.range(0, getColumnCount())
             .forEach(viewColumnIndex -> getColumn(viewColumnIndex).setPreferredWidth(getPreferredColumnWidth(viewColumnIndex)));

    myColumnPreferredWidthsSet = true;
  }

  private int getPreferredColumnWidth(int viewColumnIndex) {
    int headerWidth = getPreferredHeaderWidth(viewColumnIndex);

    OptionalInt optionalMaxCellWidth = IntStream.range(0, getRowCount())
                                                .map(viewRowIndex -> getPreferredCellWidth(viewRowIndex, viewColumnIndex))
                                                .max();

    int minColumnWidth = JBUIScale.scale(20);
    int columnWidth = Math.max(headerWidth, optionalMaxCellWidth.orElse(minColumnWidth));

    if (columnWidth < minColumnWidth) {
      return minColumnWidth;
    }

    int maxColumnWidth = JBUIScale.scale(200);

    if (columnWidth > maxColumnWidth) {
      return maxColumnWidth;
    }

    return columnWidth;
  }

  private int getPreferredHeaderWidth(int viewColumnIndex) {
    TableCellRenderer renderer = getColumn(viewColumnIndex).getHeaderRenderer();

    if (renderer == null) {
      renderer = getDefaultTableHeaderRenderer();
    }

    return getPreferredWidth(renderer, getColumnName(viewColumnIndex), -1, viewColumnIndex);
  }

  private int getPreferredCellWidth(int viewRowIndex, int viewColumnIndex) {
    TableCellRenderer renderer = getCellRenderer(viewRowIndex, viewColumnIndex);
    return getPreferredWidth(renderer, getValueAt(viewRowIndex, viewColumnIndex), viewRowIndex, viewColumnIndex);
  }

  @Override
  boolean isPastePossible() {
    if (getSelectedRowCount() != 1 || getSelectedColumnCount() != 1) {
      return false;
    }
    else {
      int column = getSelectedColumn();
      return column != StringResourceTableModel.KEY_COLUMN && column != StringResourceTableModel.UNTRANSLATABLE_COLUMN;
    }
  }

  static class ThreeStateTableRowSorter<M extends TableModel> extends TableRowSorter<M> {
    ThreeStateTableRowSorter(M model) {
      super(model);
    }

    @Override
    public void toggleSortOrder(int column) {
      List<? extends SortKey> sortKeys = getSortKeys();
      if (!sortKeys.isEmpty() && sortKeys.get(0).getSortOrder() == SortOrder.DESCENDING) {
        setSortKeys(null);
        return;
      }
      super.toggleSortOrder(column);
    }

    @Override
    public void modelStructureChanged() {
      List<? extends SortKey> sortKeys = getSortKeys();
      super.modelStructureChanged();
      setSortKeys(sortKeys);
    }
  }
}
