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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBScrollPane;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.IntUnaryOperator;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FrozenColumnTable<M extends TableModel> {
  private M myModel;

  private final int myFrozenColumnCount;
  private final Collection<FrozenColumnTableListener> myListeners;

  private SubTable<M> myFrozenTable;
  private SubTable<M> myScrollableTable;
  private SubTable<M> myLastFocusedSubTable;
  private JScrollPane myScrollPane;
  private int rowHeight;
  private int mySelectedRow;
  private int mySelectedColumn;

  @Nullable
  private FrozenColumnTableRowSorter<M> myRowSorter;

  FrozenColumnTable(@NotNull M model, int frozenColumnCount) {
    myModel = model;
    myFrozenColumnCount = frozenColumnCount;
    myListeners = new ArrayList<>();

    initFrozenTable();
    initScrollableTable();
    initScrollPane();
    myLastFocusedSubTable = myFrozenTable;

    registerActionOverrides();
    new SubTableHoverListener(myFrozenTable, myScrollableTable).install();

    mySelectedRow = -1;
    mySelectedColumn = -1;
  }

  private void registerActionOverrides() {
    registerActionOverrides(new ActionTable(myFrozenTable, myFrozenTable, myScrollableTable));
    registerActionOverrides(new ActionTable(myFrozenTable, myScrollableTable, myScrollableTable));
  }

  private void registerActionOverrides(@NotNull ActionTable table) {
    ActionMap map = table.getCurrentTable().getActionMap();
    for (ActionType type : ActionType.getEntries()) {
      map.put(type.getActionName(), new TableAction(type, table));
    }
  }

  private void initFrozenTable() {
    myFrozenTable = new SubTable<>(new SubTableModel(myModel, () -> 0, () -> myFrozenColumnCount), this);
    myFrozenTable.setName("frozenTable");

    IntUnaryOperator converter = IntUnaryOperator.identity();
    myFrozenTable.getTableHeader().addMouseListener(new HeaderPopupTriggerListener<>(converter));

    myFrozenTable.getSelectionModel().addListSelectionListener(event -> {
      if (myFrozenTable.getSelectionModel().getMinSelectionIndex() == -1) {
        myScrollableTable.clearSelection();
      }
      else {
        myScrollableTable.setRowSelectionInterval(
          myFrozenTable.getSelectionModel().getMinSelectionIndex(),
          myFrozenTable.getSelectionModel().getMaxSelectionIndex());
      }
      fireSelectedCellChanged(false);
    });

    myFrozenTable.getColumnModel().getSelectionModel().addListSelectionListener(event -> {
      if (myFrozenTable.getSelectedColumn() == -1) {
        return;
      }

      myScrollableTable.getColumnModel().getSelectionModel().clearSelection();
      fireSelectedCellChanged(false);
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

    myFrozenTable.addMouseListener(new CellPopupTriggerListener<>(converter));
    myFrozenTable.addFocusListener(new FocusAdapter() {
      public void focusGained(@NotNull FocusEvent event) {
        myLastFocusedSubTable = myFrozenTable;
      }
    });
  }

  private void initScrollableTable() {
    myScrollableTable = new SubTable<>(new SubTableModel(myModel, () -> myFrozenColumnCount, myModel::getColumnCount), this);
    myScrollableTable.setName("scrollableTable");

    IntUnaryOperator converter = viewColumnIndex -> myFrozenTable.getColumnCount() + viewColumnIndex;
    myScrollableTable.getTableHeader().addMouseListener(new HeaderPopupTriggerListener<>(converter));

    myScrollableTable.getSelectionModel().addListSelectionListener(event -> {
      if (myScrollableTable.getSelectionModel().getMinSelectionIndex() == -1) {
        myFrozenTable.clearSelection();
      }
      else {
        myFrozenTable.setRowSelectionInterval(
          myScrollableTable.getSelectionModel().getMinSelectionIndex(),
          myScrollableTable.getSelectionModel().getMaxSelectionIndex());
      }
      fireSelectedCellChanged(false);
    });

    myScrollableTable.getColumnModel().getSelectionModel().addListSelectionListener(event -> {
      if (myScrollableTable.getSelectedColumn() == -1) {
        return;
      }

      myFrozenTable.getColumnModel().getSelectionModel().clearSelection();
      fireSelectedCellChanged(false);
    });

    myScrollableTable.addMouseListener(new CellPopupTriggerListener<>(converter));
    myScrollableTable.addFocusListener(new FocusAdapter() {
      public void focusGained(@NotNull FocusEvent event) {
        myLastFocusedSubTable = myScrollableTable;
      }
    });
  }

  public boolean skipTransferTo(@NotNull Component toComponent, @NotNull Component fromComponent) {
    if (fromComponent instanceof SubTable<?>) {
      // If coming from a SubTable, then skip the next SubTable:
      return toComponent instanceof SubTable<?>;
    }
    else {
      // If coming from a different control, then skip this SubTable if this is not the last focused SubTable:
      return toComponent instanceof SubTable<?> && myLastFocusedSubTable != toComponent;
    }
  }

  private static final class HeaderPopupTriggerListener<M extends TableModel> extends MouseAdapter {
    private final IntUnaryOperator myConverter;

    private HeaderPopupTriggerListener(@NotNull IntUnaryOperator converter) {
      myConverter = converter;
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
      Point point = event.getPoint();
      int subTableIndex = header.columnAtPoint(point);

      if (subTableIndex == -1) {
        return;
      }

      @SuppressWarnings("unchecked")
      SubTable<M> subTable = (SubTable<M>)header.getTable();

      FrozenColumnTable<M> source = subTable.getFrozenColumnTable();
      int frozenColumnTableIndex = myConverter.applyAsInt(subTableIndex);
      FrozenColumnTableEvent frozenColumnTableEvent = new FrozenColumnTableEvent(source, -1, frozenColumnTableIndex, point, header);

      source.getListeners().forEach(listener -> listener.headerPopupTriggered(frozenColumnTableEvent));
    }
  }

  private void fireSelectedCellChanged(boolean force) {
    int selectedRow = getSelectedRow();
    int selectedColumn = getSelectedColumn();

    if (!force && (mySelectedRow == selectedRow && mySelectedColumn == selectedColumn)) {
      return;
    }

    mySelectedRow = selectedRow;
    mySelectedColumn = selectedColumn;

    myListeners.forEach(FrozenColumnTableListener::selectedCellChanged);
  }

  private static final class CellPopupTriggerListener<M extends TableModel> extends MouseAdapter {
    private final IntUnaryOperator myConverter;

    private CellPopupTriggerListener(@NotNull IntUnaryOperator converter) {
      myConverter = converter;
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

      @SuppressWarnings("unchecked")
      SubTable<M> subTable = (SubTable<M>)event.getSource();

      Point point = event.getPoint();
      FrozenColumnTable<M> source = subTable.getFrozenColumnTable();
      int viewRowIndex = subTable.rowAtPoint(point);
      int viewColumnIndex = myConverter.applyAsInt(subTable.columnAtPoint(point));
      FrozenColumnTableEvent frozenColumnTableEvent = new FrozenColumnTableEvent(source, viewRowIndex, viewColumnIndex, point, subTable);

      source.getListeners().forEach(listener -> listener.cellPopupTriggered(frozenColumnTableEvent));
    }
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

  public final void setRowSelectionInterval(int viewRowIndexStart, int viewRowIndexEnd) {
    myFrozenTable.setRowSelectionInterval(viewRowIndexStart, viewRowIndexEnd);
    myScrollableTable.setRowSelectionInterval(viewRowIndexStart, viewRowIndexEnd);
  }

  public final void selectCellAt(int viewRowIndex, int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    setRowSelectionInterval(viewRowIndex, viewRowIndex);
    if (viewColumnIndex < count) {
      myFrozenTable.setColumnSelectionInterval(viewColumnIndex, viewColumnIndex);
      return;
    }

    viewColumnIndex -= count;
    myScrollableTable.setColumnSelectionInterval(viewColumnIndex, viewColumnIndex);
  }

  public final int getSelectedModelRowIndex() {
    int index = myFrozenTable.getSelectedModelRowIndex();
    assert index == myScrollableTable.getSelectedModelRowIndex();

    return index;
  }

  public final int[] getSelectedModelRows() {
    int[] indexes = myFrozenTable.getSelectedRows();
    assert Arrays.equals(indexes, myScrollableTable.getSelectedRows());

    return indexes;
  }

  public final int getSelectedRowCount() {
    int count = myFrozenTable.getSelectedRowCount();
    assert count == myScrollableTable.getSelectedRowCount();

    return count;
  }

  public final int getSelectedModelColumnIndex() {
    int index = myFrozenTable.getSelectedModelColumnIndex();
    return index == -1
      ? myScrollableTable.getSelectedModelColumnIndex()
      : index;
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

  final int convertRowIndexToModel(int viewRowIndex) {
    int modelRowIndex = myFrozenTable.convertRowIndexToModel(viewRowIndex);
    assert modelRowIndex == myScrollableTable.convertRowIndexToModel(viewRowIndex);

    return modelRowIndex;
  }

  final int convertColumnIndexToModel(int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();
    JTable table;

    if (viewColumnIndex < count) {
      table = myFrozenTable;
    }
    else {
      table = myScrollableTable;
      viewColumnIndex -= count;
    }

    return ((SubTableModel)table.getModel()).convertColumnIndexToDelegate(table.convertColumnIndexToModel(viewColumnIndex));
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

  final void paste(@NotNull Transferable transferable) {
    if (!hasSelectedCell() || !transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
      return;
    }

    int row = getSelectedRow();
    int rowCount = getRowCount();

    int selectedColumn = getSelectedColumn();
    int columnCount = getColumnCount();

    List<List<String>> grid = GridPasteUtils.splitIntoGrid(getTransferDataAsString(transferable));

    for (List<String> gridRow : grid) {
      if (row >= rowCount) {
        break;
      }

      int column = selectedColumn;

      for (String gridCell : gridRow) {
        if (column >= columnCount) {
          break;
        }

        setValueAt(gridCell, row, column++);
      }

      row++;
    }
    fireSelectedCellChanged(/* force update even though the selected cell has not changed */ true);
  }

  @NotNull
  private static String getTransferDataAsString(@NotNull Transferable transferable) {
    try {
      return (String)transferable.getTransferData(DataFlavor.stringFlavor);
    }
    catch (UnsupportedFlavorException | IOException exception) {
      Logger.getInstance(FrozenColumnTable.class).warn(exception);
      return "";
    }
  }

  final void createDefaultColumnsFromModel() {
    myFrozenTable.createDefaultColumnsFromModel();
    myScrollableTable.createDefaultColumnsFromModel();
  }

  final void setDefaultRenderer(@NotNull @SuppressWarnings("SameParameterValue") Class<?> c, @NotNull TableCellRenderer renderer) {
    myFrozenTable.setDefaultRenderer(c, renderer);
    myScrollableTable.setDefaultRenderer(c, renderer);
  }

  final void setDefaultEditor(@NotNull @SuppressWarnings("SameParameterValue") Class<?> c, @NotNull TableCellEditor editor) {
    myFrozenTable.setDefaultEditor(c, editor);
    myScrollableTable.setDefaultEditor(c, editor);
  }

  @Nullable
  final FrozenColumnTableRowSorter<M> getRowSorter() {
    return myRowSorter;
  }

  final void setRowSorter(@Nullable FrozenColumnTableRowSorter<M> rowSorter) {
    if (rowSorter == null) {
      myFrozenTable.setRowSorter(null);
      myScrollableTable.setRowSorter(null);
    }
    else {
      myFrozenTable.setRowSorter(rowSorter.getFrozenTableRowSorter());
      myScrollableTable.setRowSorter(rowSorter.getScrollableTableRowSorter());
    }

    myRowSorter = rowSorter;
  }

  /** Returns whether any cell in the table is selected. */
  public final boolean hasSelectedCell() {
    return myFrozenTable.hasSelectedCell() || myScrollableTable.hasSelectedCell();
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

  @VisibleForTesting
  @NotNull
  public final List<Object> getColumnAt(int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex < count) {
      return myFrozenTable.getColumnAt(viewColumnIndex);
    }

    return myScrollableTable.getColumnAt(viewColumnIndex - count);
  }

  @NotNull
  public final Object getValueAt(int viewRowIndex, int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex < count) {
      return myFrozenTable.getValueAt(viewRowIndex, viewColumnIndex);
    }

    return myScrollableTable.getValueAt(viewRowIndex, viewColumnIndex - count);
  }

  private void setValueAt(@NotNull Object value, int viewRowIndex, int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex < count) {
      myFrozenTable.setValueAt(value, viewRowIndex, viewColumnIndex);
      return;
    }

    myScrollableTable.setValueAt(value, viewRowIndex, viewColumnIndex - count);
  }

  public final boolean editCellAt(int viewRowIndex, int viewColumnIndex) {
    int count = myFrozenTable.getColumnCount();

    if (viewColumnIndex < count) {
      return myFrozenTable.editCellAt(viewRowIndex, viewColumnIndex);
    }

    return myScrollableTable.editCellAt(viewRowIndex, viewColumnIndex - count);
  }

  @NotNull
  public final M getModel() {
    return myModel;
  }

  public void setModel(@NotNull M model) {
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

  @NotNull
  final Font getFont() {
    Font font = myFrozenTable.getFont();
    assert font.equals(myScrollableTable.getFont());

    return font;
  }
}
