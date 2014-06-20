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
import com.intellij.execution.filters.ExceptionInfoCache;
import com.intellij.execution.filters.ExceptionWorker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import gnu.trove.TIntObjectHashMap;
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
  private static final String SEPARATOR = ":";

  private JPanel myContainer;
  private JBCheckBox myGroupingCheckBox;
  private JBTable myAllocationsTable;
  private JBTextField myFilterField;
  private JBCheckBox myIncludeTraceCheckBox;
  private JBScrollPane myAllocationsPane;
  private JEditorPane myStackTraceEditorPane;

  private Project myProject;
  private AllocationInfo[] myAllocations;
  private AllocationsViewModel myViewModel;
  private TIntObjectHashMap<StackTrace> myStackTraces;

  @VisibleForTesting
  enum Column {
    ALLOCATION_ORDER("Allocation Order", Integer.class),
    ALLOCATED_CLASS("Allocated Class", String.class),
    ALLOCATION_SIZE("Allocation Size", Integer.class),
    THREAD_ID("Thread Id", Integer.class),
    ALLOCATION_SITE("Allocation Site", String.class);

    public final String description;
    public final Class dataClass;
    Column(String description, Class dataClass) {
      this.description = description;
      this.dataClass = dataClass;
    }
  }

  public AllocationsViewPanel(@NotNull Project project) {
    myProject = project;
    init();
  }

  private void init() {
    // Grouping not yet implemented
    myGroupingCheckBox.setVisible(false);
    myAllocationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myStackTraceEditorPane.addHyperlinkListener(new StackTraceListener());
    myStackTraceEditorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    myStackTraceEditorPane.setFont(myAllocationsTable.getFont());
    // Shows a text cursor inside stack trace pane so user knows text is selectable
    myStackTraceEditorPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

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

    Enumeration<TableColumn> columns = myAllocationsTable.getColumnModel().getColumns();
    while (columns.hasMoreElements()) {
      columns.nextElement().setCellRenderer(myViewModel.getCellRenderer());
    }
  }

  private void resetTableView() {
    myFilterField.setText("");
    myIncludeTraceCheckBox.setSelected(false);
    myStackTraceEditorPane.setText("");
    myAllocationsTable.getRowSorter().modelStructureChanged();

    // Clears selected row (if any) and resets focus
    myAllocationsTable.clearSelection();
    myAllocationsPane.requestFocusInWindow();

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
    myStackTraces = new TIntObjectHashMap<StackTrace>();
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
        return Column.values()[c].dataClass;
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

        if (myStackTraces.get(row) == null) {
          parseStackTrace(row);
        }
        myStackTraceEditorPane.setText(myStackTraces.get(row).toString());
        myStackTraceEditorPane.setCaretPosition(0);
      }

      private void parseStackTrace(int row) {
        StackTraceElement[] elements = myAllocations[row].getStackTrace();
        StackTrace trace = new StackTrace(elements.length);

        ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
        StringBuilder traceBuilder = new StringBuilder();
        ExceptionWorker worker = new ExceptionWorker(new ExceptionInfoCache(GlobalSearchScope.allScope(myProject)));

        for (int i = 0; i < elements.length; ++i) {
          String line = "at " + elements[i].toString();
          line = line.replaceAll("<", "&lt;");
          line = line.replaceAll(">", "&gt");
          worker.execute(line, line.length());
          PsiFile file = worker.getFile();
          VirtualFile vf;
          if (file != null) {
            vf = file.getVirtualFile();
            // If a line in the stack trace has no line number info, ExceptionWorker keeps the previous file
            if (i == 0 || !vf.equals(trace.getVirtualFile(i - 1))) {
              trace.setVirtualFile(i, vf);
              if (index.isInContent(vf)) {
                Trinity<TextRange, TextRange, TextRange> info = worker.getInfo();
                int highlightStartOffset = info.getThird().getStartOffset() + 1;
                int highlightEndOffset = info.getThird().getEndOffset();
                line = line.substring(0, highlightStartOffset)
                       + "<a href='" + row + SEPARATOR + i + "'>" + line.substring(highlightStartOffset, highlightEndOffset) + "</a>"
                       + line.substring(highlightEndOffset);
              }
            }
          }
          traceBuilder.append(line);
          traceBuilder.append("<br />");
        }
        trace.setDescription(traceBuilder.toString());
        myStackTraces.put(row, trace);
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

  private static class StackTrace {
    private String myDescription;
    private VirtualFile[] myFiles;

    public StackTrace(int depth) {
      myFiles = new VirtualFile[depth];
    }

    public void setVirtualFile(int index, @Nullable VirtualFile vf) {
      myFiles[index] = vf;
    }

    public void setDescription(@NotNull String description) {
      myDescription = description;
    }

    @Nullable
    public VirtualFile getVirtualFile(int index) {
      return myFiles[index];
    }

    @NotNull
    public String toString() {
      return myDescription;
    }
  }

  private class StackTraceListener implements HyperlinkListener {
    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        String[] fileInfo = e.getDescription().split(SEPARATOR);
        if (fileInfo.length != 2) {
          Messages.showErrorDialog("Cannot get file information", "AllocationsViewPanel");
          return;
        }
        int row = Integer.parseInt(fileInfo[0]);
        int frame = Integer.parseInt(fileInfo[1]);

        int lineNumber = myAllocations[row].getStackTrace()[frame].getLineNumber();
        VirtualFile vf = myStackTraces.get(row).getVirtualFile(frame);
        if (vf == null) {
          Messages.showErrorDialog("Cannot find file", "AllocationsViewPanel");
          return;
        }
        OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, vf, lineNumber - 1, 0);
        FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
      }
    }
  }
}