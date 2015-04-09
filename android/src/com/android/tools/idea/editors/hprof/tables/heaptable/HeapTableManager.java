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

import com.android.tools.idea.editors.hprof.tables.instancestable.InstancesTreeTable;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstancesTreeTableModel;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.util.Pair;
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

  @NotNull private List<Pair<HeapTable, InstancesTreeTable>> myHeapTables = new ArrayList<Pair<HeapTable, InstancesTreeTable>>();
  @NotNull private JBRunnerTabs myTabs;
  private Snapshot mySnapshot;
  private boolean myDominatorsComputed;

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
    for (Pair<HeapTable, InstancesTreeTable> tablePair : myHeapTables) {
      tablePair.first.notifyDominatorsComputed();
      tablePair.second.notifyDominatorsComputed();
    }
    myDominatorsComputed = true;
  }

  private void createHeapTabs() {
    Collection<Heap> heaps = mySnapshot.getHeaps();

    for (final Heap heap : heaps) {
      if (heap.getClasses().size() == 0) {
        continue;
      }

      HeapTableModel model = new HeapTableModel(HeapTableModel.createHeapTableColumns(), heap);
      final HeapTable heapTable = new HeapTable(model);

      // Use dummy data, since ListTreeTableModelOnColumns serves as both the column spec as well as the model for the table.
      final InstancesTreeTable instancesTreeTable =
        new InstancesTreeTable(new InstancesTreeTableModel(mySnapshot, heap, new ArrayList<Instance>(), myDominatorsComputed));

      myHeapTables.add(Pair.create(heapTable, instancesTreeTable));

      heapTable.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent mouseEvent) {
          super.mouseReleased(mouseEvent);
          int row = heapTable.getSelectedRow();
          if (row >= 0) {
            int modelRow = heapTable.getRowSorter().convertRowIndexToModel(row);
            ClassObj classObj = (ClassObj)heapTable.getModel().getValueAt(modelRow, 0);
            instancesTreeTable.setModel(new InstancesTreeTableModel(mySnapshot, heap, classObj.getInstances(), myDominatorsComputed));
          }
        }
      });

      JBSplitter splitter = createNavigationSplitter(heapTable, instancesTreeTable);
      myTabs.addTab(new TabInfo(splitter).setText(model.getHeapName()).setSideComponent(new JPanel()));
    }
  }
}
