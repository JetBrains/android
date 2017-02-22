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
import com.android.tools.idea.ui.TableUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.*;

public final class StringResourceTable extends JBTable implements DataProvider, PasteProvider {
  @Nullable private StringResourceTableColumnFilter myColumnFilter;

  public StringResourceTable() {
    super(new StringResourceTableModel());

    CellEditorListener editorListener = new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent event) {
        refilter();
      }

      @Override
      public void editingCanceled(ChangeEvent event) {
      }
    };

    getDefaultEditor(Boolean.class).addCellEditorListener(editorListener);
    tableHeader.setReorderingAllowed(false);

    TableCellEditor editor = new StringsCellEditor();
    editor.addCellEditorListener(editorListener);

    InputMap inputMap = getInputMap(WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete");

    setAutoResizeMode(AUTO_RESIZE_OFF);
    setCellSelectionEnabled(true);
    setDefaultEditor(String.class, editor);
    setDefaultRenderer(String.class, new StringsCellRenderer());
    setRowSorter(new ThreeStateTableRowSorter<>(getModel()));

    new TableSpeedSearch(this);
  }

  @Nullable
  public StringResourceData getData() {
    return getModel().getData();
  }

  public void refilter() {
    getRowSorter().sort();
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
    setLocaleColumnHeaderRenderers();
  }

  @Override
  public void createDefaultColumnsFromModel() {
    Map<Object, TableColumn> old = new HashMap<>();
    // Remove any current columns
    TableColumnModel columnModel = getColumnModel();
    while (columnModel.getColumnCount() != 0) {
      TableColumn col = columnModel.getColumn(0);
      old.put(col.getIdentifier(), col);
      columnModel.removeColumn(col);
    }

    StringResourceTableModel model = getModel();
    // Create new columns from the data model info
    for (int i = 0; i < model.getColumnCount(); i++) {
      Locale locale = model.getLocale(i);
      if (i < FIXED_COLUMN_COUNT || myColumnFilter == null || myColumnFilter.include(locale)) {
        TableColumn newColumn = old.get(model.getColumnName(i));
        if (newColumn == null) {
          newColumn = new TableColumn(i);
          if (i != KEY_COLUMN) {
            OptionalInt optionalWidth = getDefaultValueAndLocaleColumnPreferredWidths();
            if (optionalWidth.isPresent()) {
              newColumn.setPreferredWidth(optionalWidth.getAsInt());
            }
          }
        }
        else {
          newColumn.setModelIndex(i);
        }
        addColumn(newColumn);
      }
    }
  }

  public int getSelectedRowModelIndex() {
    return convertRowIndexToModel(getSelectedRow());
  }

  @NotNull
  public int[] getSelectedRowModelIndices() {
    return Arrays.stream(getSelectedRows())
      .map(this::convertRowIndexToModel)
      .toArray();
  }

  public int getSelectedColumnModelIndex() {
    return convertColumnIndexToModel(getSelectedColumn());
  }

  public int[] getSelectedColumnModelIndices() {
    return Arrays.stream(getSelectedColumns())
      .map(this::convertColumnIndexToModel)
      .toArray();
  }

  @Override
  public TableRowSorter<StringResourceTableModel> getRowSorter() {
    //noinspection unchecked
    return (TableRowSorter<StringResourceTableModel>)super.getRowSorter();
  }

  @Override
  public StringResourceTableModel getModel() {
    return (StringResourceTableModel)super.getModel();
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    super.setModel(model);
    TableRowSorter<StringResourceTableModel> sorter = getRowSorter();
    if (sorter != null) { // can be null when called from constructor
      sorter.setModel(getModel());
    }
    OptionalInt optionalWidth = getKeyColumnPreferredWidth();

    if (optionalWidth.isPresent()) {
      columnModel.getColumn(KEY_COLUMN).setPreferredWidth(optionalWidth.getAsInt());
    }

    if (tableHeader == null) {
      return;
    }

    setLocaleColumnHeaderRenderers();
    optionalWidth = getDefaultValueAndLocaleColumnPreferredWidths();

    if (optionalWidth.isPresent()) {
      int width = optionalWidth.getAsInt();

      IntStream.range(DEFAULT_VALUE_COLUMN, getColumnCount())
        .mapToObj(columnModel::getColumn)
        .forEach(column -> column.setPreferredWidth(width));
    }
  }

  @NotNull
  @VisibleForTesting
  public OptionalInt getKeyColumnPreferredWidth() {
    return IntStream.range(0, getRowCount())
      .map(row -> getPreferredWidth(getCellRenderer(row, KEY_COLUMN), getValueAt(row, KEY_COLUMN), row, KEY_COLUMN))
      .max();
  }

  private void setLocaleColumnHeaderRenderers() {
    TableCellRenderer renderer = new LocaleRenderer(tableHeader.getDefaultRenderer(), getModel());

    IntStream.range(FIXED_COLUMN_COUNT, getColumnCount())
      .mapToObj(columnModel::getColumn)
      .forEach(column -> column.setHeaderRenderer(renderer));
  }

  @NotNull
  @VisibleForTesting
  public OptionalInt getDefaultValueAndLocaleColumnPreferredWidths() {
    return IntStream.range(DEFAULT_VALUE_COLUMN, getColumnCount())
      .map(column -> getPreferredWidth(getHeaderRenderer(column), getColumnName(column), -1, column))
      .max();
  }

  @NotNull
  private TableCellRenderer getHeaderRenderer(int column) {
    TableCellRenderer renderer = columnModel.getColumn(column).getHeaderRenderer();
    return renderer == null ? tableHeader.getDefaultRenderer() : renderer;
  }

  private int getPreferredWidth(@NotNull TableCellRenderer renderer, @NotNull Object value, int row, int column) {
    return renderer.getTableCellRendererComponent(this, value, false, false, row, column).getPreferredSize().width + 2;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return dataId.equals(PlatformDataKeys.PASTE_PROVIDER.getName()) ? this : null;
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    if (getSelectedRowCount() != 1 || getSelectedColumnCount() != 1) {
      return false;
    }
    else {
      int column = getSelectedColumn();
      return column != KEY_COLUMN && column != UNTRANSLATABLE_COLUMN;
    }
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return isPastePossible(dataContext);
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    Transferable transferable = CopyPasteManager.getInstance().getContents();

    if (transferable != null) {
      TableUtils.paste(this, transferable);
    }
  }

  static class ThreeStateTableRowSorter<M extends TableModel> extends TableRowSorter<M> {
    public ThreeStateTableRowSorter(M model) {
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
