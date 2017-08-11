/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.ui;

import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.stream.IntStream;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

public class FixedColumnTable extends JBTable implements PropertyChangeListener {

  private int fixedColumnCount;
  private final JTable fixed;

  public FixedColumnTable(@NotNull TableModel model) {
    super(model);
    addPropertyChangeListener(this);

    fixed = new JBTable() {
      @Override
      public int getRowHeight() {
        return FixedColumnTable.this.getRowHeight();
      }

      @Override
      public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
        if (!toggle && !extend) {
          clearSelectionAndAnchorAndLead(FixedColumnTable.this.getColumnModel().getSelectionModel());
        }
        super.changeSelection(rowIndex, columnIndex, toggle, extend);
      }
    };
    fixed.setAutoCreateColumnsFromModel(false);
    fixed.setModel(model);
    fixed.setSelectionModel(getSelectionModel());

    // this allows the fixed cols headers to be resized.
    setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    fixed.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

    fixed.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(@NotNull ComponentEvent event) {
        JViewport fixedViewport = (JViewport)SwingUtilities.getUnwrappedParent(fixed);
        Dimension size = fixedViewport.getPreferredSize();
        size.width = fixed.getWidth();
        fixedViewport.setPreferredSize(size);
        getScrollPane().revalidate();
      }
    });

    fixed.setInputMap(WHEN_FOCUSED, getInputMap(WHEN_FOCUSED));
    fixed.setActionMap(getActionMap());
  }

  public int[] getSelectedColumnModelIndices() {
    return IntStream.concat(Arrays.stream(fixed.getSelectedColumns()).map(fixed::convertColumnIndexToModel),
                            Arrays.stream(getSelectedColumns()).map(this::convertColumnIndexToModel)).toArray();
  }

  @Override
  public void removeEditor() {
    super.removeEditor();
    fixed.removeEditor();
  }

  @Override
  public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
    if (!toggle && !extend) {
      clearSelectionAndAnchorAndLead(fixed.getColumnModel().getSelectionModel());
    }
    super.changeSelection(rowIndex, columnIndex, toggle, extend);
  }

  private static void clearSelectionAndAnchorAndLead(@NotNull ListSelectionModel selectionModel) {
    selectionModel.clearSelection();
    selectionModel.setAnchorSelectionIndex(-1);
    selectionModel.setLeadSelectionIndex(-1);
  }

  @Override
  public void setDefaultRenderer(@NotNull Class<?> columnClass, @Nullable TableCellRenderer renderer) {
    super.setDefaultRenderer(columnClass, renderer);
    if (fixed != null) {
      fixed.setDefaultRenderer(columnClass, renderer);
    }
  }

  @Override
  public void setDefaultEditor(@NotNull Class<?> columnClass, @NotNull TableCellEditor editor) {
    super.setDefaultEditor(columnClass, editor);
    if (fixed != null) {
      fixed.setDefaultEditor(columnClass, editor);
    }
  }

  @Override
  public void setCellSelectionEnabled(boolean cellSelectionEnabled) {
    super.setCellSelectionEnabled(cellSelectionEnabled);
    fixed.setCellSelectionEnabled(cellSelectionEnabled);
  }

  @Override
  public void setRowSorter(@NotNull RowSorter<? extends TableModel> sorter) {
    super.setRowSorter(sorter);
    fixed.setRowSorter(sorter);
  }

  @Override
  protected void configureEnclosingScrollPane() {
    super.configureEnclosingScrollPane();
    JScrollPane scrollPane = getScrollPane();
    if (scrollPane != null) {
      scrollPane.setViewportBorder(null);
      scrollPane.setRowHeaderView(fixed);
      scrollPane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, fixed.getTableHeader());
    }
  }

  @Nullable
  private JScrollPane getScrollPane() {
    Container parent = SwingUtilities.getUnwrappedParent(this);
    if (parent instanceof JViewport) {
      Container grandparent = parent.getParent();
      if (grandparent instanceof JScrollPane) {
        JScrollPane scrollPane = (JScrollPane)grandparent;
        JViewport viewport = scrollPane.getViewport();
        if (viewport == null || SwingUtilities.getUnwrappedView(viewport) != this) {
          return null;
        }
        return scrollPane;
      }
    }
    return null;
  }

  public int getTotalColumnCount() {
    return (fixed == null ? 0 : fixed.getColumnCount()) + getColumnCount();
  }

  public void setFixedColumnCount(int fixedColumnCount) {
    this.fixedColumnCount = fixedColumnCount;
    updateFixedColumns();
  }

  @Override
  public void addColumn(@NotNull TableColumn column) {
    if (column.getModelIndex() < fixedColumnCount) {
      fixed.addColumn(column);
    }
    else {
      super.addColumn(column);
    }
  }

  @Override
  public void removeColumn(@NotNull TableColumn column) {
    if (column.getModelIndex() < fixedColumnCount) {
      fixed.removeColumn(column);
    }
    else {
      super.removeColumn(column);
    }
  }

  @NotNull
  @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
  public TableColumn getColumn(int columnIndex) {
    if (columnIndex < fixed.getColumnCount()) {
      return fixed.getColumnModel().getColumn(columnIndex);
    }
    else {
      return getColumnModel().getColumn(columnIndex - fixed.getColumnCount());
    }
  }

  @NotNull
  public TableCellRenderer getCellRendererAtModel(int row, int column) {
    if (column < fixed.getColumnCount()) {
      return fixed.getCellRenderer(fixed.convertRowIndexToView(row), fixed.convertColumnIndexToView(column));
    }
    else {
      return getCellRenderer(convertRowIndexToView(row), convertColumnIndexToView(column));
    }
  }

  @NotNull
  public Object getFixedColumnValueAt(int row, int column) {
    return fixed.getValueAt(row, column);
  }

  private void updateFixedColumns() {
    TableColumnModel model1 = fixed.getColumnModel();
    TableColumnModel model2 = getColumnModel();

    for (int col = model1.getColumnCount() - 1; col >= 0; col--) {
      TableColumn col1 = model1.getColumn(col);
      if (col1.getModelIndex() >= fixedColumnCount) {
        model1.removeColumn(col1);
        model2.addColumn(col1);
        model2.moveColumn(model2.getColumnCount(), 0);
      }
    }

    for (int col = 0; col < model2.getColumnCount(); col++) {
      TableColumn col1 = model2.getColumn(col);
      if (col1.getModelIndex() < fixedColumnCount) {
        model2.removeColumn(col1);
        model1.addColumn(col1);
        col--;
      }
    }

    fixed.setPreferredScrollableViewportSize(fixed.getPreferredSize());
  }

  @Override
  public void propertyChange(@NotNull PropertyChangeEvent event) {
    String name = event.getPropertyName();
    if ("selectionModel".equals(name)) {
      fixed.setSelectionModel(getSelectionModel());
    }
    else if ("model".equals(name)) {
      fixed.setModel(getModel());
      updateFixedColumns();
    }
  }
}