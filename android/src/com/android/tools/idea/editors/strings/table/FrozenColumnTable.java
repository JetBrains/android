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
import com.intellij.ui.table.JBTable;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
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
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
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

  // Row and Column index that were sent via the FrozenColumnTableListeners
  private int myLastSelectedRow;
  private int myLastSelectedColumn;

  // Column selection across the 2 tables:
  private int myAnchorColumn;
  private int mySelectedColumn;

  @Nullable
  private FrozenColumnTableRowSorter<M> myRowSorter;
  private final CellSelectionListener<M> myCellSelectionListener;

  FrozenColumnTable(@NotNull M model, int frozenColumnCount) {
    myModel = model;
    myFrozenColumnCount = frozenColumnCount;
    myListeners = new ArrayList<>();
    myCellSelectionListener = new CellSelectionListener<>();

    initFrozenTable();
    initScrollableTable();
    initScrollPane();
    myLastFocusedSubTable = myFrozenTable;

    registerActionOverrides();
    new SubTableHoverListener(myFrozenTable, myScrollableTable).install();

    myLastSelectedRow = -1;
    myLastSelectedColumn = -1;

    myAnchorColumn = -1;
    mySelectedColumn = -1;
  }

  private void registerActionOverrides() {
    registerActionOverrides(myFrozenTable);
    registerActionOverrides(myScrollableTable);
  }

  private void registerActionOverrides(@NotNull JTable table) {
    ActionMap map = table.getActionMap();
    for (ActionType type : ActionType.getEntries()) {
      map.put(type.getActionName(), new TableAction(type, this));
    }
  }

  private void initFrozenTable() {
    myFrozenTable = new SubTable<>(new SubTableModel(myModel, () -> 0, () -> myFrozenColumnCount), this);
    myFrozenTable.setName("frozenTable");

    IntUnaryOperator converter = IntUnaryOperator.identity();
    myFrozenTable.getTableHeader().addMouseListener(new HeaderPopupTriggerListener<>(converter));

    myFrozenTable.getSelectionModel().addListSelectionListener(event -> {
      mirrorRowSelection(myFrozenTable, myScrollableTable);
      fireSelectedCellChanged(false);
    });

    myFrozenTable.getColumnModel().getSelectionModel().addListSelectionListener(event -> {
      removeEmptyColumnSelection(myFrozenTable);
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
    myFrozenTable.addMouseListener(myCellSelectionListener);
    myFrozenTable.addMouseMotionListener(myCellSelectionListener);
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
      mirrorRowSelection(myScrollableTable, myFrozenTable);
      fireSelectedCellChanged(false);
    });

    myScrollableTable.getColumnModel().getSelectionModel().addListSelectionListener(event -> {
      removeEmptyColumnSelection(myScrollableTable);
      fireSelectedCellChanged(false);
    });

    myScrollableTable.addMouseListener(new CellPopupTriggerListener<>(converter));
    myScrollableTable.addMouseListener(myCellSelectionListener);
    myScrollableTable.addMouseMotionListener(myCellSelectionListener);
    myScrollableTable.addFocusListener(new FocusAdapter() {
      public void focusGained(@NotNull FocusEvent event) {
        myLastFocusedSubTable = myScrollableTable;
      }
    });
  }

  private void mirrorRowSelection(@NotNull JTable fromTable, JTable toTable) {
    ListSelectionModel fsm = fromTable.getSelectionModel();
    int first = fsm.getMinSelectionIndex();
    int last = fsm.getMaxSelectionIndex();
    if (first == -1) {
      toTable.clearSelection();
    }
    else {
      if (fsm.getLeadSelectionIndex() == first) {
        int temp = first;
        first = last;
        last = temp;
      }
      toTable.setRowSelectionInterval(first, last);
    }
  }

  private void removeEmptyColumnSelection(@NotNull JTable table) {
    ListSelectionModel sm = table.getColumnModel().getSelectionModel();
    if ((mySelectedColumn < 0 || myAnchorColumn < 0) && !sm.isSelectionEmpty()) {
      mySelectedColumn = adjustedColumnIndex(sm.getLeadSelectionIndex(), table);
      myAnchorColumn = adjustedColumnIndex(sm.getAnchorSelectionIndex(), table);
    }
  }

  private int adjustedColumnIndex(int index, JTable table) {
    if (index < 0) {
      return index;
    }
    return table == myFrozenTable ? index : index + getFrozenColumnCount();
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

    if (!force && (myLastSelectedRow == selectedRow && myLastSelectedColumn == selectedColumn)) {
      return;
    }

    myLastSelectedRow = selectedRow;
    myLastSelectedColumn = selectedColumn;

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

  private static final class CellSelectionListener<M extends TableModel> extends MouseAdapter {

    @Override
    public void mousePressed(@NotNull MouseEvent event) {
      @SuppressWarnings("unchecked")
      SubTable<M> subTable = (SubTable<M>)event.getSource();
      FrozenColumnTable<M> source = subTable.getFrozenColumnTable();

      Point point = event.getPoint();
      int viewRowIndex = subTable.rowAtPoint(point);
      int viewColumnIndex = source.adjustedColumnIndex(subTable.columnAtPoint(point), subTable);
      boolean extend = event.isShiftDown();
      source.gotoColumn(viewColumnIndex, extend);
      int viewRowAnchor = !extend || subTable.getSelectionModel().isSelectionEmpty()
                          ? viewRowIndex
                          : subTable.getSelectionModel().getAnchorSelectionIndex();
      subTable.getSelectionModel().setSelectionInterval(viewRowAnchor, viewRowIndex);
      event.consume();
    }

    @Override
    public void mouseDragged(@NotNull MouseEvent event) {
      @SuppressWarnings("unchecked")
      SubTable<M> subTable = (SubTable<M>)event.getSource();
      FrozenColumnTable<M> source = subTable.getFrozenColumnTable();

      Point point = event.getPoint();
      int viewRowIndex = subTable.rowAtPoint(point);
      int viewColumnIndex = source.adjustedColumnIndex(subTable.columnAtPoint(point), subTable);
      if (viewColumnIndex < 0) {
        JTable otherTable = source.getFrozenTable() == subTable ? source.getScrollableTable() : source.getFrozenTable();
        point = SwingUtilities.convertPoint(subTable, point, otherTable);
        viewColumnIndex = source.adjustedColumnIndex(otherTable.columnAtPoint(point), otherTable);
      }
      source.gotoColumn(viewColumnIndex, true);
      int viewRowAnchor =
        subTable.getSelectionModel().isSelectionEmpty() ? viewRowIndex : subTable.getSelectionModel().getAnchorSelectionIndex();
      subTable.getSelectionModel().setSelectionInterval(viewRowAnchor, viewRowIndex);
      event.consume();
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
    setRowSelectionInterval(viewRowIndex, viewRowIndex);
    gotoColumn(viewColumnIndex, false);
  }

  public final int getSelectedModelRowIndex() {
    int row = getSelectedRow();
    return myFrozenTable.convertRowIndexToModel(row);
  }

  public final int[] getSelectedModelRows() {
    int[] indexes = myFrozenTable.getSelectedRows();
    assert Arrays.equals(indexes, myScrollableTable.getSelectedRows());
    return Arrays.stream(indexes).map(index -> convertRowIndexToModel(index)).toArray();
  }

  public final int getSelectedRowCount() {
    int count = myFrozenTable.getSelectedRowCount();
    assert count == myScrollableTable.getSelectedRowCount();

    return count;
  }

  public final int getSelectedModelColumnIndex() {
    int column = getSelectedColumn();
    return column >= 0 ? convertColumnIndexToModel(column) : -1;
  }

  public final void selectAll() {
    if (getRowCount() == 0) {
      return;
    }
    myFrozenTable.changeSelection(getRowCount() - 1, getFrozenColumnCount() - 1, false, false);
    myScrollableTable.changeSelection(getRowCount() - 1, getScrollableTable().getColumnCount() - 1, false, false);
    myFrozenTable.changeSelection(0, 0, false, true);
    myScrollableTable.changeSelection(0, 0, false, true);
    myAnchorColumn = getColumnCount() - 1;
    mySelectedColumn = 0;
    scrollIntoView();
  }

  public final void clearSelection() {
    myAnchorColumn = -1;
    mySelectedColumn = -1;
    myFrozenTable.clearSelection();
    myScrollableTable.clearSelection();
  }

  // Override row movement when not extending the selection: This should clear the selection in the other table.
  public final void gotoRow(int row) {
    // Note: extendFromAnchor is false for all row actions in TableActions.
    if (getRowCount() == 0) {
      return;
    }
    row = Math.max(0, Math.min(row, getRowCount() - 1));
    int column = Math.max(0, mySelectedColumn);
    myFrozenTable.getSelectionModel().setSelectionInterval(row, row);
    myScrollableTable.getSelectionModel().setSelectionInterval(row, row);
    int fixedColumns = getFrozenColumnCount();
    if (column < fixedColumns) {
      myFrozenTable.getColumnModel().getSelectionModel().setSelectionInterval(column, column);
      myScrollableTable.getColumnModel().getSelectionModel().clearSelection();
    }
    else {
      myFrozenTable.getColumnModel().getSelectionModel().clearSelection();
      myScrollableTable.getColumnModel().getSelectionModel().setSelectionInterval(column - fixedColumns, column - fixedColumns);
    }
    myAnchorColumn = column;
    mySelectedColumn = column;
    fireSelectedCellChanged(false);
    scrollIntoView();
  }

  // Override row PgUp, PgDn when not extending the selection: This should clear the selection in the other table.
  public final void scrollRow(boolean forwards) {
    // Code taken from BasicTableUI:
    int selectedRow = getSelectedRow();
    if (selectedRow < 0 || !(SwingUtilities.getUnwrappedParent(myFrozenTable).getParent() instanceof JScrollPane)) {
      return;
    }
    Dimension delta = myFrozenTable.getParent().getSize();
    Rectangle r = myFrozenTable.getCellRect(selectedRow, 0, true);
    if (forwards) {
      // scroll by at least one cell
      r.y += Math.max(delta.height, r.height);
    } else {
      r.y -= delta.height;
    }

    int newRow = myFrozenTable.rowAtPoint(r.getLocation());
    if (newRow == -1 && forwards) {
      newRow = getRowCount();
    }
    gotoRow(newRow);
  }

  public final void gotoColumn(int column, boolean extendFromAnchor) {
    if (!extendFromAnchor) {
      myAnchorColumn = -1;

      int row = getSelectedRow();
      myFrozenTable.getSelectionModel().setSelectionInterval(row, row);
    }
    if (column < getFrozenColumnCount()) {
      gotoFrozenColumn(column);
    }
    else {
      gotoScrollableColumn(column - getFrozenColumnCount());
    }
    fireSelectedCellChanged(false);
    scrollIntoView();
  }

  private void gotoFrozenColumn(int column) {
    final int targetColumn = Math.max(0, column);
    if (myAnchorColumn < 0) {
      myAnchorColumn = targetColumn;
    }

    int scrollableAnchor = myAnchorColumn - getFrozenColumnCount();
    if (scrollableAnchor < 0) {
      myScrollableTable.getColumnModel().getSelectionModel().clearSelection();
    }
    else {
      myScrollableTable.getColumnModel().getSelectionModel().setSelectionInterval(scrollableAnchor, 0);
    }

    int frozenAnchor = Math.min(myAnchorColumn, getFrozenColumnCount() - 1);
    myFrozenTable.getColumnModel().getSelectionModel().setSelectionInterval(frozenAnchor, targetColumn);

    if (!myFrozenTable.hasFocus()) {
      myFrozenTable.requestFocus();
    }
    mySelectedColumn = targetColumn;
  }

  private void gotoScrollableColumn(int column) {
    final int targetColumn = Math.min(myScrollableTable.getColumnCount() - 1, column);
    if (myAnchorColumn < 0) {
      myAnchorColumn = targetColumn + getFrozenColumnCount();
    }

    if (myAnchorColumn >= myFrozenTable.getColumnCount()) {
      myFrozenTable.getColumnModel().getSelectionModel().clearSelection();
    }
    else {
      myFrozenTable.getColumnModel().getSelectionModel().setSelectionInterval(myAnchorColumn, getFrozenColumnCount() - 1);
    }

    int scrollableAnchor = Math.max(myAnchorColumn - getFrozenColumnCount(), 0);
    myScrollableTable.getColumnModel().getSelectionModel().setSelectionInterval(scrollableAnchor, targetColumn);

    if (!myScrollableTable.hasFocus()) {
      myScrollableTable.requestFocus();
    }
    mySelectedColumn = targetColumn + getFrozenColumnCount();
  }

  private void scrollIntoView() {
    int row = getSelectedRow();
    int column = mySelectedColumn;
    if (row < 0 || column < 0) {
      return;
    }
    JTable table = myFrozenTable;
    if (column >= getFrozenColumnCount()) {
      table = myScrollableTable;
      column -= getFrozenColumnCount();
    }
    Rectangle cellRect = table.getCellRect(row, column, false);
    if (cellRect != null) {
      table.scrollRectToVisible(cellRect);
    }
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
    return getSelectedRow() >= 0 && getSelectedColumn() >= 0;
  }

  public final int getSelectedRow() {
    int row = getSelectedRow(myFrozenTable);
    assert row == getSelectedRow(myScrollableTable);

    return row;
  }

  private int getSelectedRow(@NotNull JTable table) {
    ListSelectionModel sm = table.getSelectionModel();
    return sm.isSelectionEmpty() ? -1 : sm.getLeadSelectionIndex();
  }

  public final int getSelectedColumn() {
    return mySelectedColumn;
  }

  public final int getAnchorColumn() {
    return myAnchorColumn;
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
