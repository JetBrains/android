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

import com.google.common.collect.Maps;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.JBTable;
import java.awt.Dimension;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SubTable<M extends TableModel> extends JBTable implements DataProvider, PasteProvider {
  private final FrozenColumnTable<M> myFrozenColumnTable;

  SubTable(@NotNull SubTableModel model, @NotNull FrozenColumnTable<M> frozenColumnTable) {
    super(model);

    getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    getColumnModel().getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    setCellSelectionEnabled(true);

    TableSpeedSearch.installOn(this);
    myFrozenColumnTable = frozenColumnTable;
  }

  @NotNull
  FrozenColumnTable<M> getFrozenColumnTable() {
    return myFrozenColumnTable;
  }

  void setSelectedRow(int selectedViewRowIndex) {
    if (getSelectedRow() == selectedViewRowIndex) {
      return;
    }

    if (selectedViewRowIndex == -1) {
      getSelectionModel().clearSelection();
      return;
    }

    setRowSelectionInterval(selectedViewRowIndex, selectedViewRowIndex);
  }

  @NotNull
  int[] getSelectedModelRowIndices() {
    return Arrays.stream(getSelectedRows())
      .map(this::convertRowIndexToModel)
      .toArray();
  }

  @NotNull
  int[] getSelectedModelColumnIndices() {
    SubTableModel model = (SubTableModel)getModel();

    return Arrays.stream(getSelectedColumns())
      .map(this::convertColumnIndexToModel)
      .map(model::convertColumnIndexToDelegate)
      .toArray();
  }

  @NotNull
  List<Object> getColumnAt(int viewColumnIndex) {
    return IntStream.range(0, getRowCount())
      .mapToObj(viewRowIndex -> getValueAt(viewRowIndex, viewColumnIndex))
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  protected JTableHeader createDefaultTableHeader() {
    JTableHeader header = new JBTableHeader();
    header.setReorderingAllowed(false);
    // Without this header of FrozenColumnTable.myFrozenTable is not visible when there is no column in FrozenColumnTable.myScrollableTable.
    header.setPreferredSize(new Dimension(1, super.getRowHeight()));
    return header;
  }

  @Override
  public int getRowHeight() {
    int subTableRowHeight = super.getRowHeight();
    int frozenColumnTableRowHeight = myFrozenColumnTable.getRowHeight();

    if (subTableRowHeight > frozenColumnTableRowHeight) {
      myFrozenColumnTable.setRowHeight(subTableRowHeight);
      return subTableRowHeight;
    }

    return frozenColumnTableRowHeight;
  }

  @Override
  public void createDefaultColumnsFromModel() {
    if (myFrozenColumnTable == null) {
      return;
    }

    addColumns(removeAllColumns());
  }

  private void addColumns(@NotNull Map<Integer, TableColumn> map) {
    SubTableModel model = (SubTableModel)getModel();

    IntStream.range(0, dataModel.getColumnCount())
      .map(model::convertColumnIndexToDelegate)
      .filter(myFrozenColumnTable::includeColumn)
      .map(model::convertColumnIndexToModel)
      .mapToObj(modelColumnIndex -> getOrCreateColumn(map, modelColumnIndex, model))
      .forEach(this::addColumn);
  }

  @NotNull
  private TableColumn getOrCreateColumn(@NotNull Map<Integer, TableColumn> map, int modelColumnIndex, @NotNull SubTableModel model) {
    TableColumn column = map.get(modelColumnIndex);

    if (column == null) {
      column = myFrozenColumnTable.createColumn(model.convertColumnIndexToDelegate(modelColumnIndex));
      column.setModelIndex(modelColumnIndex);
    }

    column.setHeaderValue(null);
    return column;
  }

  @NotNull
  private Map<Integer, TableColumn> removeAllColumns() {
    Map<Integer, TableColumn> map = Maps.newHashMapWithExpectedSize(columnModel.getColumnCount());

    while (columnModel.getColumnCount() != 0) {
      TableColumn column = columnModel.getColumn(0);

      removeColumn(column);
      map.put(column.getModelIndex(), column);
    }

    return map;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return PlatformDataKeys.PASTE_PROVIDER.is(dataId) ? this : null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    return myFrozenColumnTable.isPastePossible();
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return myFrozenColumnTable.isPastePossible();
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    Transferable transferable = CopyPasteManager.getInstance().getContents();

    if (transferable == null) {
      return;
    }

    myFrozenColumnTable.paste(transferable);
  }

  @Override
  public Dimension getPreferredSize() {
    if (getColumnCount() == 0 && myFrozenColumnTable.getFrozenTable().getColumnCount() > 0) {
      // Allow the vertical scrollbar to show even when there are no locales defined (b/165896691)
      Dimension size = myFrozenColumnTable.getFrozenTable().getPreferredSize();
      size.width = 0;
      return size;
    }
    return super.getPreferredSize();
  }

  @Override
  public String toString() {
    return getName();
  }
}
