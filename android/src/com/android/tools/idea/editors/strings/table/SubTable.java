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

import com.android.tools.adtui.TableUtils;
import com.google.common.collect.Maps;
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
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.IntStream;

final class SubTable extends JBTable implements DataProvider, PasteProvider {
  private final FrozenColumnTable myFrozenColumnTable;

  SubTable(@NotNull SubTableModel model, @NotNull FrozenColumnTable frozenColumnTable) {
    super(model);

    getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    getColumnModel().getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    setCellSelectionEnabled(true);
    addMouseListener(new CellPopupTriggerListener(this));

    new TableSpeedSearch(this);
    myFrozenColumnTable = frozenColumnTable;
  }

  private static final class CellPopupTriggerListener extends MouseAdapter {
    private final SubTable mySubTable;

    private CellPopupTriggerListener(@NotNull SubTable subTable) {
      mySubTable = subTable;
    }

    @Override
    public void mousePressed(@NotNull MouseEvent event) {
      mousePressedOrReleased(event);
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent event) {
      mousePressedOrReleased(event);
    }

    private void mousePressedOrReleased(@NotNull MouseEvent event) {
      if (!event.isPopupTrigger()) {
        return;
      }

      FrozenColumnTable source = mySubTable.myFrozenColumnTable;
      Point point = event.getPoint();
      int viewRowIndex = mySubTable.rowAtPoint(point);
      int viewColumnIndex = mySubTable.columnAtPoint(point);

      FrozenColumnTableEvent frozenColumnTableEvent = new FrozenColumnTableEvent(
        source,
        mySubTable.convertRowIndexToModel(viewRowIndex),
        ((SubTableModel)mySubTable.getModel()).convertColumnIndexToDelegate(mySubTable.convertColumnIndexToModel(viewColumnIndex)),
        point,
        mySubTable);

      source.getListeners().forEach(listener -> listener.cellPopupTriggered(frozenColumnTableEvent));
    }
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
  @Override
  protected JTableHeader createDefaultTableHeader() {
    JTableHeader header = new JBTableHeader();

    header.setReorderingAllowed(false);
    header.addMouseListener(new HeaderPopupTriggerListener(this));

    return header;
  }

  private static final class HeaderPopupTriggerListener extends MouseAdapter {
    private final SubTable mySubTable;

    private HeaderPopupTriggerListener(@NotNull SubTable subTable) {
      mySubTable = subTable;
    }

    @Override
    public void mousePressed(@NotNull MouseEvent event) {
      mousePressedOrReleased(event);
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent event) {
      mousePressedOrReleased(event);
    }

    private void mousePressedOrReleased(@NotNull MouseEvent event) {
      if (!event.isPopupTrigger()) {
        return;
      }

      JTableHeader header = (JTableHeader)event.getSource();
      int viewColumnIndex = header.columnAtPoint(event.getPoint());

      if (viewColumnIndex == -1) {
        return;
      }

      FrozenColumnTable source = mySubTable.myFrozenColumnTable;

      FrozenColumnTableEvent frozenColumnTableEvent = new FrozenColumnTableEvent(
        source,
        -1,
        ((SubTableModel)mySubTable.getModel()).convertColumnIndexToDelegate(mySubTable.convertColumnIndexToModel(viewColumnIndex)),
        event.getPoint(),
        header);

      source.getListeners().forEach(listener -> listener.headerPopupTriggered(frozenColumnTableEvent));
    }
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
    return dataId.equals(PlatformDataKeys.PASTE_PROVIDER.getName()) ? this : null;
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

    if (transferable != null) {
      TableUtils.paste(this, transferable);
    }
  }

  @Override
  public String toString() {
    return getName();
  }
}
