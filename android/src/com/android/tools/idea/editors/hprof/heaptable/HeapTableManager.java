/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof.heaptable;

import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.table.DefaultTableCellHeaderRenderer;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HeapTableManager {
  private static final int DIVIDER_WIDTH = 4;

  @NotNull private List<JBTable> myHeapTables = new ArrayList<JBTable>();
  @NotNull private JBRunnerTabs myTabs;
  private Snapshot mySnapshot;

  public HeapTableManager(@NotNull JBRunnerTabs tabs) {
    myTabs = tabs;
  }

  @NotNull
  public static JBSplitter createNavigationSplitter(@Nullable JComponent leftPanelContents, @Nullable JComponent rightPanelContents) {
    JBPanel navigationPanel = new JBPanel(new BorderLayout());
    navigationPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    navigationPanel.setBackground(JBColor.background());
    if (leftPanelContents != null) {
      JBScrollPane scrollPane = new JBScrollPane();
      scrollPane.setViewportView(leftPanelContents);
      navigationPanel.add(scrollPane, BorderLayout.CENTER);
    }

    JBPanel contextInformationPanel = new JBPanel();
    contextInformationPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    contextInformationPanel.setBackground(JBColor.background());
    if (rightPanelContents != null) {
      JBScrollPane scrollPane = new JBScrollPane();
      scrollPane.setViewportView(rightPanelContents);
      contextInformationPanel.add(scrollPane, BorderLayout.CENTER);
    }

    JBSplitter navigationSplitter = new JBSplitter(false);
    navigationSplitter.setFirstComponent(navigationPanel);
    navigationSplitter.setSecondComponent(contextInformationPanel);
    navigationSplitter.setDividerWidth(DIVIDER_WIDTH);

    return navigationSplitter;
  }

  public void setSnapshot(@NotNull Snapshot snapshot) {
    mySnapshot = snapshot;
    createHeapTabs();
  }

  private void createHeapTabs() {
    Collection<Heap> heaps = mySnapshot.getHeaps();

    for (Heap heap : heaps) {
      if (heap.getClasses().size() == 0) {
        continue;
      }

      HeapTableModel model = new HeapTableModel(HeapTableModel.createDefaultHeapTableColumns(), heap);
      JBTable table = new JBTable(model);
      table.setAutoCreateRowSorter(true);
      myHeapTables.add(table);

      JBSplitter splitter = createNavigationSplitter(table, new JBList());
      myTabs.addTab(new TabInfo(splitter).setText(model.getHeapName()));
    }

    prettifyHeapTables();
  }

  private void prettifyHeapTables() {
    for (JBTable table : myHeapTables) {
      table.getRowSorter().toggleSortOrder(0);
    }

    for (JBTable table : myHeapTables) {
      TableModel tableModel = table.getModel();
      assert (tableModel instanceof HeapTableModel);
      HeapTableModel heapTableModel = (HeapTableModel)tableModel;

      for (int i = 0; i < table.getColumnModel().getColumnCount(); ++i) {
        TableColumn column = table.getColumnModel().getColumn(i);
        column.setPreferredWidth(heapTableModel.getColumnWidth(i));

        DefaultTableCellHeaderRenderer headerRenderer = new DefaultTableCellHeaderRenderer();
        headerRenderer.setHorizontalAlignment(heapTableModel.getColumnHeaderJustification(i));
        column.setHeaderRenderer(headerRenderer);
      }
    }
  }
}
