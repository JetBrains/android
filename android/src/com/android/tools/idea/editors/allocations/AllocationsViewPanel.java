/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.allocations;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AllocationInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class AllocationsViewPanel {
  @VisibleForTesting
  static final String GROUPING_CHECKBOX_NAME = "myGroupingCheckBox";
  @VisibleForTesting
  static final String ALLOCATIONS_TABLE_NAME = "myAllocationsTable";
  private static final String COLUMN_ORDER_PROPERTY = "android.allocationsview.column.ordering";
  private static final String COLUMN_WIDTH_PROPERTY = "android.allocationsview.column.widths";

  private JPanel myContainer;
  private JBCheckBox myGroupingCheckBox;
  private JBTextField myFilterField;
  private JBCheckBox myIncludeTraceCheckBox;
  private JBSplitter mySplitter;

  private JBTable myAllocationsTable;
  private JBScrollPane myAllocationsPane;
  private ConsoleView myConsoleView;

  private Project myProject;
  private PropertiesComponent myProperties;

  private AllocationInfo[] myAllocations;
  private AllocationsViewModel myViewModel;

  @VisibleForTesting
  enum Column {
    ALLOCATION_ORDER("Allocation Order", 0),
    ALLOCATED_CLASS("Allocated Class", "com.sample.data.AllocatedClass"),
    ALLOCATION_SIZE("Allocation Size", 0),
    THREAD_ID("Thread Id", 0),
    ALLOCATION_SITE("Allocation Site", "com.sample.data.AllocationSite.method(AllocationSite.java:000)");

    public final String description;
    public final Object sampleData;
    Column(String description, Object sampleData) {
      this.description = description;
      this.sampleData = sampleData;
    }
  }

  public AllocationsViewPanel(@NotNull Project project) {
    myProject = project;
    init();
  }

  private void init() {
    myProperties = getProperties();

    // Grouping not yet implemented
    myGroupingCheckBox.setVisible(false);

    myAllocationsTable = new JBTable();
    myAllocationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myAllocationsPane = new JBScrollPane(myAllocationsTable);

    mySplitter.setFirstComponent(myAllocationsPane);

    myConsoleView = getConsoleView();
    if (myConsoleView != null) {
      mySplitter.setSecondComponent(myConsoleView.getComponent());
    }

    // Lets ViewPanelSortTest find these components for testing
    myGroupingCheckBox.setName(GROUPING_CHECKBOX_NAME);
    myAllocationsTable.setName(ALLOCATIONS_TABLE_NAME);
  }

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  private void setupTableView() {
    myViewModel = new AllocationsViewModel();

    myIncludeTraceCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myViewModel.updateFilter();
      }
    });

    myFilterField.getDocument().addDocumentListener(myViewModel.getFilterListener());
    myAllocationsTable.getSelectionModel().addListSelectionListener(myViewModel.getRowListener());

    myAllocationsTable.setModel(myViewModel.getTableModel());
    myAllocationsTable.setRowSorter(myViewModel.getRowSorter());

    myAllocationsTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
      @Override
      public void columnAdded(TableColumnModelEvent e) {
      }

      @Override
      public void columnRemoved(TableColumnModelEvent e) {
      }

      @Override
      public void columnMoved(TableColumnModelEvent e) {
        Column[] columns = Column.values();
        String[] columnOrdering = new String[columns.length];
        for (int i = 0; i < columnOrdering.length; ++i) {
          columnOrdering[i] = Integer.toString(myAllocationsTable.getColumnModel().getColumn(i).getModelIndex());
        }
        setValues(COLUMN_ORDER_PROPERTY, columnOrdering);
      }

      @Override
      public void columnMarginChanged(ChangeEvent e) {
        saveColumnWidths();
      }

      @Override
      public void columnSelectionChanged(ListSelectionEvent e) {
      }
    });

    String[] columnOrdering = getValues(COLUMN_ORDER_PROPERTY);
    if (columnOrdering != null) {
      for (int i = 0; i < columnOrdering.length; ++i) {
        myAllocationsTable.getColumnModel().moveColumn(myAllocationsTable.convertColumnIndexToView(Integer.parseInt(columnOrdering[i])), i);
      }
    }

    String[] columnWidths = getValues(COLUMN_WIDTH_PROPERTY);
    if (columnWidths == null) {
      columnWidths = getDefaultColumnWidths();
    }

    for (Column column : Column.values()) {
      TableColumn tableColumn = myAllocationsTable.getColumn(column.description);
      tableColumn.setCellRenderer(myViewModel.getCellRenderer());
      tableColumn.setPreferredWidth(Integer.parseInt(columnWidths[column.ordinal()]));
    }
    saveColumnWidths();
  }

  private String[] getDefaultColumnWidths() {
    Column[] columns = Column.values();
    int cumulativeWidth = 0;
    String[] defaultWidths = new String[columns.length];
    FontMetrics metrics = myAllocationsTable.getFontMetrics(myAllocationsTable.getFont());
    for (Column column : columns) {
      // Multiples width by ~1.5 so text is not right against column sides
      int columnWidth = 3 * Math.max(metrics.stringWidth(column.description), metrics.stringWidth(String.valueOf(column.sampleData))) / 2;
      defaultWidths[column.ordinal()] = Integer.toString(columnWidth);
      if (column != Column.ALLOCATION_SITE) {
        cumulativeWidth += columnWidth;
      }
    }
    // If possible, uses remaining width, which makes the table respect the preferred column widths exactly.
    int remainingWidth = myAllocationsTable.getWidth() - cumulativeWidth;
    if (remainingWidth > 0) {
      defaultWidths[Column.ALLOCATION_SITE.ordinal()] = Integer.toString(remainingWidth);
    }
    return defaultWidths;
  }

  private void saveColumnWidths() {
    Column[] columns = Column.values();
    String[] widths = new String[columns.length];
    for (Column column : columns) {
      widths[column.ordinal()] = Integer.toString(myAllocationsTable.getColumn(column.description).getWidth());
    }
    setValues(COLUMN_WIDTH_PROPERTY, widths);
  }

  private void resetTableView() {
    myFilterField.setText("");
    myIncludeTraceCheckBox.setSelected(false);
    myConsoleView.clear();

    // Clears selected row (if any) and resets focus
    myAllocationsTable.clearSelection();
    myAllocationsPane.requestFocusInWindow();

    myAllocationsTable.getRowSorter().allRowsChanged();
    myAllocationsTable.updateUI();

    myAllocationsPane.getVerticalScrollBar().setValue(0);
    myAllocationsPane.getHorizontalScrollBar().setValue(0);
  }

  public void setAllocations(@NotNull AllocationInfo[] allocations) {
    myAllocations = allocations;
    if (myViewModel == null) {
      setupTableView();
    } else {
      resetTableView();
    }
  }

  @VisibleForTesting
  @Nullable
  PropertiesComponent getProperties() {
    return PropertiesComponent.getInstance();
  }

  @VisibleForTesting
  void setValues(@NotNull String property, @NotNull String[] values) {
    myProperties.setValues(property, values);
  }

  @VisibleForTesting
  @Nullable
  String[] getValues(@NotNull String property) {
    return myProperties.getValues(property);
  }

  @VisibleForTesting
  @Nullable
  ConsoleView getConsoleView() {
    return TextConsoleBuilderFactory.getInstance().createBuilder(myProject).getConsole();
  }

  private class AllocationsViewModel {
    private final TableModel myTableModel = new AllocationsTableModel();
    private final TableRowSorter<TableModel> myRowSorter = new AllocationsRowSorter();
    private final ListSelectionListener myRowListener = new AllocationsRowListener();
    private final DocumentListener myFilterListener = new AllocationsFilterListener();
    private final TableCellRenderer myCellRenderer = new DefaultCellRenderer();

    @NotNull
    public TableModel getTableModel() {
      return myTableModel;
    }

    @NotNull
    public TableRowSorter<TableModel> getRowSorter() {
      return myRowSorter;
    }

    @NotNull
    public ListSelectionListener getRowListener() {
      return myRowListener;
    }

    @NotNull
    public DocumentListener getFilterListener() {
      return myFilterListener;
    }

    @NotNull
    public TableCellRenderer getCellRenderer() {
      return myCellRenderer;
    }

    private void updateFilter() {
      getRowSorter().setRowFilter(new AllocationsRowFilter());
    }

    private class AllocationsTableModel extends AbstractTableModel {
      @Override
      public int getRowCount() {
        return myAllocations.length;
      }

      @Override
      public int getColumnCount() {
        return Column.values().length;
      }

      @Nullable
      private Object getValueAt(@NotNull AllocationInfo data, int column) {
        switch (Column.values()[column]) {
          case ALLOCATION_ORDER:
            return data.getAllocNumber();
          case ALLOCATED_CLASS:
            return data.getAllocatedClass();
          case ALLOCATION_SIZE:
            return data.getSize();
          case THREAD_ID:
            return data.getThreadId();
          case ALLOCATION_SITE:
            return data.getAllocationSite();
          default:
            return null;
        }
      }

      @Override
      @Nullable
      public Object getValueAt(int row, int column) {
        return getValueAt(myAllocations[row], column);
      }

      @Override
      @NotNull
      public String getColumnName(int column) {
        return Column.values()[column].description;
      }

      @Override
      @NotNull
      public Class getColumnClass(int c) {
        return Column.values()[c].sampleData.getClass();
      }
    }

    private class AllocationsRowSorter extends TableRowSorter<TableModel> {
      public AllocationsRowSorter() {
        setModel(getTableModel());
        setMaxSortKeys(1);
      }

      @Override
      public void setSortKeys(@Nullable List<? extends SortKey> sortKeys) {
        List<SortKey> keys = sortKeys == null ? new ArrayList<SortKey>() : new ArrayList<SortKey>(sortKeys);
        // Does secondary sorting (breaks ties) on allocation size
        if (keys.size() > 0 && keys.get(0).getColumn() != Column.ALLOCATION_SIZE.ordinal()) {
          keys.add(1, new SortKey(Column.ALLOCATION_SIZE.ordinal(), keys.get(0).getSortOrder()));
        }
        super.setSortKeys(keys);
      }
    }

    private class AllocationsRowListener implements ListSelectionListener {
      private int getModelRow(int viewRow) {
        return myAllocationsTable.convertRowIndexToModel(viewRow);
      }

      @Override
      public void valueChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
          return;
        }
        int viewRow = myAllocationsTable.getSelectedRow();
        if (viewRow < 0) {
          return;
        }
        int row = getModelRow(viewRow);
        if (row < 0) {
          return;
        }

        myConsoleView.clear();
        myConsoleView.print(getStackTrace(row), ConsoleViewContentType.NORMAL_OUTPUT);
        myConsoleView.scrollTo(0);
      }

      private String getStackTrace(int row) {
        StringBuilder stackTrace = new StringBuilder();
        StackTraceElement[] stackTraceElements = myAllocations[row].getStackTrace();
        for (StackTraceElement element : stackTraceElements) {
          stackTrace.append("at ");
          stackTrace.append(element.toString());
          stackTrace.append("\n");
        }
        return stackTrace.toString();
      }
    }

    private class AllocationsFilterListener implements DocumentListener {
      @Override
      public void changedUpdate(@Nullable DocumentEvent e) {
        updateFilter();
      }

      @Override
      public void insertUpdate(DocumentEvent e) {
        updateFilter();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        updateFilter();
      }
    }

    private class AllocationsRowFilter extends RowFilter<TableModel,Integer> {
      @Override
      public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
        return myAllocations[entry.getIdentifier()].filter(myFilterField.getText(),
                myIncludeTraceCheckBox.isSelected(), myFilterField.getLocale());
      }
    }

    private class DefaultCellRenderer extends ColoredTableCellRenderer {
      @Override
      public void customizeCellRenderer(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
        append(value.toString());
        if (value instanceof Number) {
          this.setTextAlign(SwingConstants.RIGHT);
        } else {
          this.setTextAlign(SwingConstants.LEFT);
        }
      }
    }
  }
}