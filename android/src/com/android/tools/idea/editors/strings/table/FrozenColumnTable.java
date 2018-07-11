/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class FrozenColumnTable {
  private TableModel myModel;

  private final int myFrozenColumnCount;
  private final Collection<FrozenColumnTableListener> myListeners;

  private SubTable myFrozenTable;
  private SubTable myScrollableTable;
  private JScrollPane myScrollPane;
  private int rowHeight;
  private int mySelectedRow;
  private int mySelectedColumn;

  FrozenColumnTable(@NotNull TableModel model, int frozenColumnCount) {
    myModel = model;
    myFrozenColumnCount = frozenColumnCount;
    myListeners = new ArrayList<>();

    initFrozenTable();
    initScrollableTable();
    initScrollPane();

    mySelectedRow = -1;
    mySelectedColumn = -1;
  }

  private void initFrozenTable() {
    myFrozenTable = new SubTable(new SubTableModel(myModel, () -> 0, () -> myFrozenColumnCount), this);

    myFrozenTable.getSelectionModel().addListSelectionListener(event -> {
      myScrollableTable.setSelectedRow(myFrozenTable.getSelectedRow());
      fireSelectedCellChanged();
    });

    myFrozenTable.getColumnModel().getSelectionModel().addListSelectionListener(event -> {
      if (myFrozenTable.getSelectedColumn() == -1) {
        return;
      }

      myScrollableTable.getColumnModel().getSelectionModel().clearSelection();
      fireSelectedCellChanged();
    });

    myFrozenTable.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(@NotNull ComponentEvent event) {
        Component header = myScrollPane.getRowHeader();

        Dimension size = header.getPreferredSize();
        size.width = myFrozenTable.getWidth();

        header.setPreferredSize(size);
        myScrollPane.revalidate();
      }
    });
  }

  private void initScrollableTable() {
    myScrollableTable = new SubTable(new SubTableModel(myModel, () -> myFrozenColumnCount, myModel::getColumnCount), this);

    myScrollableTable.getSelectionModel().addListSelectionListener(event -> {
      myFrozenTable.setSelectedRow(myScrollableTable.getSelectedRow());
      fireSelectedCellChanged();
    });

    myScrollableTable.getColumnModel().getSelectionModel().addListSelectionListener(event -> {
      if (myScrollableTable.getSelectedColumn() == -1) {
        return;
      }

      myFrozenTable.getColumnModel().getSelectionModel().clearSelection();
      fireSelectedCellChanged();
    });
  }

  private void fireSelectedCellChanged() {
    int selectedRow = getSelectedRow();
    int selectedColumn = getSelectedColumn();

    if (mySelectedRow == selectedRow && mySelectedColumn == selectedColumn) {
      return;
    }

    mySelectedRow = selectedRow;
    mySelectedColumn = selectedColumn;

    myListeners.forEach(listener -> listener.selectedCellChanged());
  }

  private void initScrollPane() {
    myScrollPane = new JBScrollPane(myScrollableTable);

    myScrollPane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, myFrozenTable.getTableHeader());
    myScrollPane.setRowHeaderView(myFrozenTable);
  }

  boolean includeColumn(int modelColumnIndex) {
    return true;
  }

  @NotNull
  TableColumn createColumn(int modelColumnIndex) {
    return new TableColumn(modelColumnIndex);
  }

  public final void selectCellAt(int viewRowIndex, int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex < count) {
      myFrozenTable.setRowSelectionInterval(viewRowIndex, viewRowIndex);
      myFrozenTable.setColumnSelectionInterval(viewColumnIndex, viewColumnIndex);

      return;
    }

    viewColumnIndex -= count;

    myScrollableTable.setRowSelectionInterval(viewRowIndex, viewRowIndex);
    myScrollableTable.setColumnSelectionInterval(viewColumnIndex, viewColumnIndex);
  }

  public final int getSelectedModelRowIndex() {
    int index = myFrozenTable.convertRowIndexToModel(myFrozenTable.getSelectedRow());
    assert index == myScrollableTable.convertRowIndexToModel(myScrollableTable.getSelectedRow());

    return index;
  }

  public final int getSelectedModelColumnIndex() {
    int index = getSelectedColumn();

    if (index == -1) {
      return -1;
    }

    int count = myFrozenTable.getColumnCount();
    JTable table;

    if (index < count) {
      table = myFrozenTable;
    }
    else {
      table = myScrollableTable;
      index -= count;
    }

    return ((SubTableModel)table.getModel()).convertColumnIndexToDelegate(table.getColumnModel().getColumn(index).getModelIndex());
  }

  public final int[] getSelectedModelRowIndices() {
    int[] indices = myFrozenTable.getSelectedModelRowIndices();
    assert Arrays.equals(indices, myScrollableTable.getSelectedModelRowIndices());

    return indices;
  }

  public final int[] getSelectedModelColumnIndices() {
    int[] frozenIndices = myFrozenTable.getSelectedModelColumnIndices();
    int[] scrollableIndices = myScrollableTable.getSelectedModelColumnIndices();

    int[] indices = new int[frozenIndices.length + scrollableIndices.length];

    System.arraycopy(frozenIndices, 0, indices, 0, frozenIndices.length);
    System.arraycopy(scrollableIndices, 0, indices, frozenIndices.length, scrollableIndices.length);

    return indices;
  }

  public final int getFrozenColumnCount() {
    return myFrozenTable.getColumnCount();
  }

  @NotNull
  public final TableColumn getColumn(int viewColumnIndex) {
    if (viewColumnIndex < myFrozenTable.getColumnCount()) {
      return myFrozenTable.getColumnModel().getColumn(viewColumnIndex);
    }

    return myScrollableTable.getColumnModel().getColumn(viewColumnIndex - myFrozenTable.getColumnCount());
  }

  @NotNull
  final TableCellRenderer getDefaultTableHeaderRenderer() {
    return myFrozenTable.getTableHeader().getDefaultRenderer();
  }

  @NotNull
  final TableCellRenderer getCellRenderer(int viewRowIndex, int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex < count) {
      return myFrozenTable.getCellRenderer(viewRowIndex, viewColumnIndex);
    }

    return myScrollableTable.getCellRenderer(viewRowIndex, viewColumnIndex - count);
  }

  final int getPreferredWidth(@NotNull TableCellRenderer renderer, @NotNull Object value, int viewRowIndex, int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();
    JTable table;

    if (viewColumnIndex < count) {
      table = myFrozenTable;
    }
    else {
      table = myScrollableTable;
      viewColumnIndex -= count;
    }

    return renderer.getTableCellRendererComponent(table, value, false, false, viewRowIndex, viewColumnIndex).getPreferredSize().width + 2;
  }

  @NotNull
  public final JTable getFrozenTable() {
    return myFrozenTable;
  }

  @NotNull
  public final JTable getScrollableTable() {
    return myScrollableTable;
  }

  @NotNull
  public final Component getScrollPane() {
    return myScrollPane;
  }

  final int getRowHeight() {
    return rowHeight;
  }

  final void setRowHeight(int rowHeight) {
    this.rowHeight = rowHeight;
  }

  final void putInInputMap(@NotNull KeyStroke keyStroke, @SuppressWarnings("SameParameterValue") @NotNull Object actionMapKey) {
    myFrozenTable.getInputMap().put(keyStroke, actionMapKey);
    myScrollableTable.getInputMap().put(keyStroke, actionMapKey);
  }

  public final void putInActionMap(@NotNull Object key, @NotNull Action action) {
    myFrozenTable.getActionMap().put(key, action);
    myScrollableTable.getActionMap().put(key, action);
  }

  public final void addFrozenColumnTableListener(@NotNull FrozenColumnTableListener listener) {
    myListeners.add(listener);
  }

  @NotNull
  final Iterable<FrozenColumnTableListener> getListeners() {
    return myListeners;
  }

  boolean isPastePossible() {
    return false;
  }

  final void createDefaultColumnsFromModel() {
    myFrozenTable.createDefaultColumnsFromModel();
    myScrollableTable.createDefaultColumnsFromModel();
  }

  final void setDefaultRenderer(@NotNull @SuppressWarnings("SameParameterValue") Class<?> c, @NotNull TableCellRenderer renderer) {
    myFrozenTable.setDefaultRenderer(c, renderer);
    myScrollableTable.setDefaultRenderer(c, renderer);
  }

  @Nullable
  public final TableCellEditor getDefaultEditor(@NotNull Class<?> c) {
    TableCellEditor editor = myFrozenTable.getDefaultEditor(c);
    assert editor == myScrollableTable.getDefaultEditor(c);

    return editor;
  }

  final void setDefaultEditor(@NotNull @SuppressWarnings("SameParameterValue") Class<?> c, @NotNull TableCellEditor editor) {
    myFrozenTable.setDefaultEditor(c, editor);
    myScrollableTable.setDefaultEditor(c, editor);
  }

  @NotNull
  RowSorter<? extends TableModel> getRowSorter() {
    RowSorter<? extends TableModel> sorter = myFrozenTable.getRowSorter();
    assert sorter == myScrollableTable.getRowSorter();

    return sorter;
  }

  final void setRowSorter(@NotNull RowSorter<? extends TableModel> sorter) {
    myFrozenTable.setRowSorter(sorter);
    myScrollableTable.setRowSorter(sorter);
  }

  public final void setRowSelectionInterval(int viewRowIndex1, int viewRowIndex2) {
    myFrozenTable.setRowSelectionInterval(viewRowIndex1, viewRowIndex2);
    assert Arrays.equals(myFrozenTable.getSelectedRows(), myScrollableTable.getSelectedRows());
  }

  public final void setColumnSelectionInterval(int viewColumnIndex1, int viewColumnIndex2) {
    assert viewColumnIndex1 <= viewColumnIndex2;
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex2 < count) {
      myFrozenTable.setColumnSelectionInterval(viewColumnIndex1, viewColumnIndex2);
      return;
    }

    if (viewColumnIndex1 >= count) {
      myScrollableTable.setColumnSelectionInterval(viewColumnIndex1 - count, viewColumnIndex2 - count);
      return;
    }

    throw new UnsupportedOperationException();
  }

  public final int getSelectedRow() {
    int row = myFrozenTable.getSelectedRow();
    assert row == myScrollableTable.getSelectedRow();

    return row;
  }

  public final int getSelectedColumn() {
    int column = myFrozenTable.getSelectedColumn();

    if (column != -1) {
      return column;
    }

    column = myScrollableTable.getSelectedColumn();

    if (column != -1) {
      return myFrozenTable.getColumnCount() + column;
    }

    return -1;
  }

  public final int getSelectedRowCount() {
    int count = myFrozenTable.getSelectedRowCount();
    assert count == myScrollableTable.getSelectedRowCount();

    return count;
  }

  public final int getSelectedColumnCount() {
    return myFrozenTable.getSelectedColumnCount() + myScrollableTable.getSelectedColumnCount();
  }

  public final int getRowCount() {
    int count = myFrozenTable.getRowCount();
    assert count == myScrollableTable.getRowCount();

    return count;
  }

  public final int getColumnCount() {
    return myFrozenTable.getColumnCount() + myScrollableTable.getColumnCount();
  }

  @NotNull
  public final String getColumnName(int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex < count) {
      return myFrozenTable.getColumnName(viewColumnIndex);
    }

    return myScrollableTable.getColumnName(viewColumnIndex - count);
  }

  @NotNull
  public final Object getValueAt(int viewRowIndex, int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex < count) {
      return myFrozenTable.getValueAt(viewRowIndex, viewColumnIndex);
    }

    return myScrollableTable.getValueAt(viewRowIndex, viewColumnIndex - count);
  }

  public final int rowAtPoint(@NotNull Point point) {
    int row = myFrozenTable.rowAtPoint(point);

    if (row != -1) {
      return row;
    }

    return myScrollableTable.rowAtPoint(point);
  }

  public final int columnAtPoint(@NotNull Point point) {
    int column = myFrozenTable.columnAtPoint(point);

    if (column != -1) {
      return column;
    }

    column = myScrollableTable.columnAtPoint(point);

    if (column == -1) {
      return -1;
    }

    return myFrozenTable.getColumnCount() + column;
  }

  public final boolean editCellAt(int viewRowIndex, int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex < count) {
      return myFrozenTable.editCellAt(viewRowIndex, viewColumnIndex);
    }

    return myScrollableTable.editCellAt(viewRowIndex, viewColumnIndex - count);
  }

  @NotNull
  public TableModel getModel() {
    return myModel;
  }

  public void setModel(@NotNull TableModel model) {
    myModel = model;

    myFrozenTable.setModel(new SubTableModel(model, () -> 0, () -> myFrozenColumnCount));
    myScrollableTable.setModel(new SubTableModel(model, () -> myFrozenColumnCount, model::getColumnCount));
  }

  @Nullable
  public final TableCellEditor getCellEditor() {
    if (myFrozenTable.isEditing()) {
      return myFrozenTable.getCellEditor();
    }

    return myScrollableTable.getCellEditor();
  }
}
