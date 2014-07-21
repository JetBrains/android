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

import com.android.annotations.Nullable;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class AllocationsRowListener implements ListSelectionListener {
  private final JBTable myAllocationsTable;
  private final ConsoleView myConsoleView;

  public AllocationsRowListener(@NotNull JBTable allocationsTable, @Nullable ConsoleView consoleView) {
    myAllocationsTable = allocationsTable;
    myConsoleView = consoleView;
  }

  @Override
  public void valueChanged(ListSelectionEvent event) {
    if (myConsoleView == null) {
      return;
    }
    if (event.getValueIsAdjusting()) {
      return;
    }
    int viewRow = myAllocationsTable.getSelectedRow();
    if (viewRow < 0) {
      return;
    }

    int row = myAllocationsTable.convertRowIndexToModel(viewRow);
    myConsoleView.clear();
    myConsoleView.print(getStackTrace(row), ConsoleViewContentType.NORMAL_OUTPUT);
    myConsoleView.scrollTo(0);
  }

  @NotNull
  private String getStackTrace(int row) {
    StringBuilder stackTrace = new StringBuilder();
    StackTraceElement[] stackTraceElements = ((AllocationsTableModel) myAllocationsTable.getModel()).getAllocation(row).getStackTrace();
    for (StackTraceElement element : stackTraceElements) {
      stackTrace.append("at ");
      stackTrace.append(element.toString());
      stackTrace.append("\n");
    }
    return stackTrace.toString();
  }
}