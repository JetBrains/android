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

import com.android.ide.common.resources.Locale;
import com.android.tools.idea.editors.strings.StringResourceData;
import com.android.tools.idea.editors.strings.table.filter.StringResourceTableColumnFilter;
import com.android.tools.idea.editors.strings.table.filter.StringResourceTableRowFilter;
import com.intellij.util.ui.JBUI;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import javax.swing.KeyStroke;
import javax.swing.SortOrder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringResourceTable extends FrozenColumnTable<StringResourceTableModel> {
  private final TableCellRenderer myLocaleRenderer;

  @Nullable
  private StringResourceTableColumnFilter myColumnFilter;

  private boolean myColumnPreferredWidthsSet;

  public StringResourceTable() {
    super(new StringResourceTableModel(), 4);

    setDefaultEditor(String.class, new StringTableCellEditor());
    setDefaultRenderer(String.class, new StringsCellRenderer());
    setRowSorter(new FrozenColumnTableRowSorter<>(new ThreeStateTableRowSorter<>(getModel()), this));

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
    FrozenColumnTableRowSorter<StringResourceTableModel> sorter = getRowSorter();
    assert sorter != null;

    return (StringResourceTableRowFilter)sorter.getRowFilter();
  }

  public void setRowFilter(@Nullable StringResourceTableRowFilter filter) {
    FrozenColumnTableRowSorter<StringResourceTableModel> sorter = getRowSorter();
    assert sorter != null;

    sorter.setRowFilter(filter);
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

  @Override
  public void setModel(@NotNull StringResourceTableModel model) {
    super.setModel(model);
    setRowSorter(new FrozenColumnTableRowSorter<>(new ThreeStateTableRowSorter<>(model), this));

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

    int minColumnWidth = JBUI.scale(20);
    int columnWidth = Math.max(headerWidth, optionalMaxCellWidth.orElse(minColumnWidth));

    if (columnWidth < minColumnWidth) {
      return minColumnWidth;
    }

    int maxColumnWidth = JBUI.scale(200);

    return Math.min(columnWidth, maxColumnWidth);
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
    return hasSelectedCell() && isColumnValidPasteTarget(getSelectedColumn());
  }

  private static boolean isColumnValidPasteTarget(int column) {
    return column != StringResourceTableModel.KEY_COLUMN && column != StringResourceTableModel.UNTRANSLATABLE_COLUMN;
  }

  static class ThreeStateTableRowSorter<M extends TableModel> extends TableRowSorter<M> {
    private ThreeStateTableRowSorter(M model) {
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
