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
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class AllocationsViewPanel {
  private JPanel myContainer;
  private JBCheckBox myGroupingCheckBox;
  private JBTable myAllocationsTable;
  private JBLabel myFilterLabel;
  private JBTextField myFilterField;
  private JBCheckBox myIncludeTraceCheckBox;
  private JPanel myGroupingPanel;
  private JPanel myFilterPanel;
  private JPanel myCustomizingPanel;
  private JBScrollPane myAllocationsPane;
  private JBScrollPane myStackTracePane;
  private JSplitPane mySplitPane;
  private JEditorPane myStackTraceEditorPane;

  private Project myProject;
  private AllocationInfo[] myAllocations;
  private TIntObjectHashMap<StackTrace> myStackTraces;
  private ExceptionWorker myExceptionWorker;

  private static String SEPARATOR = ":";

  public AllocationsViewPanel(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  private void showAllocations() {
    // Disables grouping and filtering (not yet implemented)
    myGroupingCheckBox.setEnabled(false);
    myFilterField.setEnabled(false);
    myFilterField.setText("Coming soon");
    myIncludeTraceCheckBox.setEnabled(false);

    myAllocationsTable.setModel(new AllocationsTableModel());
    myAllocationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myAllocationsTable.getSelectionModel().addListSelectionListener(new RowListener());

    myStackTraces = new TIntObjectHashMap<StackTrace>();
    myStackTraceEditorPane.addHyperlinkListener(new StackTraceListener());
    myStackTraceEditorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    myStackTraceEditorPane.setFont(myAllocationsTable.getFont());

    /*
     * Keeps focus in table if there is a currently selected row and none of the grouping checkbox, the filter text field, and the include
     * trace checkbox is selected.  Allows user to continue scrolling through table with arrow keys after returning from a file linked to in
     * the stack trace.
     */
    myStackTraceEditorPane.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myAllocationsTable.getSelectedRow() >= 0) {
          myAllocationsTable.requestFocusInWindow();
        }
      }
    });
  }

  public void setAllocations(@NotNull AllocationInfo[] allocations) {
    myAllocations = allocations;
    showAllocations();
  }

  private class AllocationsTableModel extends AbstractTableModel {
    private final String[] COLUMN_NAMES = new String[] {
      "Allocation Order", "Allocated Class", "Allocation Size", "Thread Id", "Class Allocated In", "Method Allocated In"
    };

    private final Object[] DUMMY_DATA = new Object[] {0, "", 0, 0, "", ""};

    @Override
    public int getRowCount() {
      return myAllocations.length;
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NAMES.length;
    }

    @Override
    @Nullable
    public Object getValueAt(int row, int column) {
      AllocationInfo data = myAllocations[row];
      switch (column) {
        case 0:
          return data.getAllocNumber();
        case 1:
          return data.getAllocatedClass();
        case 2:
          return data.getSize();
        case 3:
          return data.getThreadId();
        case 4:
          return data.getFirstTraceClassName();
        case 5:
          return data.getFirstTraceMethodName();
        default:
          return null;
      }
    }

    @Override
    @NotNull
    public String getColumnName(int column) {
      return COLUMN_NAMES[column];
    }

    @Override
    @NotNull
    public Class getColumnClass(int c) {
      return DUMMY_DATA[c].getClass();
    }
  }

  private class RowListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent event) {
      if (event.getValueIsAdjusting()) { return; }
      int row = myAllocationsTable.convertRowIndexToModel(myAllocationsTable.getSelectedRow());

      if (myStackTraces.get(row) == null) {
        StackTraceElement[] elements = myAllocations[row].getStackTrace();
        StackTrace trace = new StackTrace(elements.length);

        if (myExceptionWorker == null) {
          myExceptionWorker = new ExceptionWorker(new ExceptionInfoCache(GlobalSearchScope.allScope(myProject)));
        }
        ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
        StringBuilder traceBuilder = new StringBuilder();

        for (int i = 0; i < elements.length; ++i) {
          String line = "at " + elements[i].toString();
          line = line.replaceAll("<", "&lt;");
          line = line.replaceAll(">", "&gt");
          myExceptionWorker.execute(line, line.length());
          PsiFile file = myExceptionWorker.getFile();
          VirtualFile vf;
          if (file != null) {
            vf = file.getVirtualFile();
            // If a line in the stack trace has no line number info, ExceptionWorker keeps the previous file
            if (i == 0 || !vf.equals(trace.getVirtualFile(i - 1))) {
              trace.setVirtualFile(i, vf);
              if (index.isInContent(vf)) {
                Trinity<TextRange, TextRange, TextRange> info = myExceptionWorker.getInfo();
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
      myStackTraceEditorPane.setText(myStackTraces.get(row).toString());
      myStackTraceEditorPane.setCaretPosition(0);
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