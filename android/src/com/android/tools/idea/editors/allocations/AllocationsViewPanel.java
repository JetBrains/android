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
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.config.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AllocationsViewPanel {
  @VisibleForTesting
  static final String GROUPING_CHECKBOX_NAME = "myGroupingCheckBox";
  @VisibleForTesting
  static final String ALLOCATIONS_TABLE_NAME = "myAllocationsTable";

  private JPanel myContainer;
  private JBCheckBox myGroupingCheckBox;
  private JBTextField myFilterField;
  private JBCheckBox myIncludeTraceCheckBox;
  private JBTable myAllocationsTable;
  private JBScrollPane myAllocationsPane;
  private ConsoleView myConsoleView;
  private JBSplitter mySplitter;

  public AllocationsViewPanel(@NotNull Project project) {
    init(project);
  }

  private void init(Project project) {
    // Grouping not yet implemented
    myGroupingCheckBox.setVisible(false);

    myAllocationsTable = new JBTable();
    myConsoleView = createConsoleView(project);
    AllocationsTableUtil.setUpTable(getStorage(), myAllocationsTable, myConsoleView);
    AllocationsFilterUtil.setUpFiltering(myAllocationsTable, myFilterField, myIncludeTraceCheckBox);

    myAllocationsPane = new JBScrollPane(myAllocationsTable);
    mySplitter.setFirstComponent(myAllocationsPane);
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

  private void resetView() {
    myFilterField.setText("");
    myIncludeTraceCheckBox.setSelected(false);

    // Clears selected row (if any) and resets focus
    myAllocationsTable.clearSelection();
    myAllocationsPane.requestFocusInWindow();

    myAllocationsPane.getVerticalScrollBar().setValue(0);
    myAllocationsPane.getHorizontalScrollBar().setValue(0);

    if (myConsoleView != null) {
      myConsoleView.clear();
    }
  }

  public void setAllocations(@NotNull final AllocationInfo[] allocations) {
    ((AllocationsTableModel) myAllocationsTable.getModel()).setAllocations(allocations);
    resetView();
  }

  @VisibleForTesting
  @Nullable
  ConsoleView createConsoleView(@NotNull Project project) {
    return TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
  }

  @VisibleForTesting
  @Nullable
  Storage.PropertiesComponentStorage getStorage() {
    return new Storage.PropertiesComponentStorage("android.allocationsview.colummns");
  }
}