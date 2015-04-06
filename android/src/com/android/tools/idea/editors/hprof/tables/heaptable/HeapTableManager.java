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
package com.android.tools.idea.editors.hprof.tables.heaptable;

import com.android.tools.idea.editors.hprof.tables.instancestable.InstancesTable;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstancesTableModel;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HeapTableManager {
  private static final int DIVIDER_WIDTH = 4;

  @NotNull private List<HeapTable> myHeapTables = new ArrayList<HeapTable>();
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

    JBPanel contextInformationPanel = new JBPanel(new BorderLayout());
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

  public void notifyDominatorsComputed() {
    for (HeapTable table : myHeapTables) {
      table.notifyDominatorsComputed();
    }
  }

  private void createHeapTabs() {
    Collection<Heap> heaps = mySnapshot.getHeaps();

    for (Heap heap : heaps) {
      if (heap.getClasses().size() == 0) {
        continue;
      }

      HeapTableModel model = new HeapTableModel(HeapTableModel.createHeapTableColumns(), heap);
      final HeapTable heapTable = new HeapTable(model);
      myHeapTables.add(heapTable);

      final InstancesTable instancesTable = new InstancesTable(new InstancesTableModel(mySnapshot));
      final Heap closedHeap = heap;

      heapTable.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent mouseEvent) {
          super.mouseClicked(mouseEvent);
          int row = heapTable.getSelectedRow();
          if (row >= 0) {
            int modelRow = heapTable.getRowSorter().convertRowIndexToModel(row);
            ClassObj classObj = (ClassObj)heapTable.getModel().getValueAt(modelRow, 0);
            instancesTable.setInstances(closedHeap, classObj.getInstances());
          }
        }
      });

      JBSplitter splitter = createNavigationSplitter(heapTable, instancesTable);
      myTabs.addTab(new TabInfo(splitter).setText(model.getHeapName()).setSideComponent(null));
    }
  }
}
