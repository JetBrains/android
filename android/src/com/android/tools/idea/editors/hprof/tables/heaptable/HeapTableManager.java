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

import com.android.tools.idea.editors.hprof.HprofViewPanel;
import com.android.tools.idea.editors.hprof.ComputeDominatorAction;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstanceDetailModel;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstanceDetailView;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstancesTreeTable;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstancesTreeTableModel;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class HeapTableManager {
  private static final int DIVIDER_WIDTH = 4;

  @NotNull private Set<HeapTable> myHeapTables = new HashSet<HeapTable>();
  @NotNull private Project myProject;
  @NotNull HprofViewPanel myRootPanel;
  @NotNull private JBRunnerTabs myParentContainer;
  private Snapshot mySnapshot;
  private boolean myDominatorsComputed;

  public HeapTableManager(@NotNull Project project, @NotNull HprofViewPanel rootPanel, @NotNull JBRunnerTabs parentContainer) {
    myProject = project;
    myParentContainer = parentContainer;
    myRootPanel = rootPanel;
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
    for (HeapTable heapTable : myHeapTables) {
      heapTable.notifyDominatorsComputed();
    }
    myDominatorsComputed = true;
  }

  private void createHeapTabs() {
    Collection<Heap> heaps = mySnapshot.getHeaps();

    for (final Heap heap : heaps) {
      if (heap.getClasses().size() == 0) {
        continue;
      }

      // Use dummy data, since ListTreeTableModelOnColumns serves as both the column spec as well as the model for the table.
      final InstancesTreeTable instancesTreeTable =
        new InstancesTreeTable(new InstancesTreeTableModel(mySnapshot, heap, new ArrayList<Instance>(), myDominatorsComputed));

      HeapTableModel model = new HeapTableModel(HeapTableModel.createHeapTableColumns(), heap);
      final HeapTable heapTable = new HeapTable(model, instancesTreeTable);

      myHeapTables.add(heapTable);

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

      instancesTreeTable.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          super.mouseReleased(e);

          if (e.getClickCount() < 2) {
            return;
          }

          int row = instancesTreeTable.getSelectedRow();
          if (row >= 0) {
            RowSorter<? extends TableModel> sorter = instancesTreeTable.getRowSorter();
            int modelRow = sorter == null ? row : sorter.convertRowIndexToModel(row);
            Instance detailsRoot = (Instance)instancesTreeTable.getModel().getValueAt(modelRow, 0);

            String idString = Long.toHexString(detailsRoot.getId());
            if (myRootPanel.findDetailPanel(idString) != null) {
              return;
            }

            InstanceDetailView detailTree =
              new InstanceDetailView(new InstanceDetailModel(mySnapshot, heap, detailsRoot, myDominatorsComputed));
            myRootPanel.createDetailPanel(idString, detailTree, new JPanel());
          }
        }
      });

      JBSplitter splitter = createNavigationSplitter(heapTable, instancesTreeTable);
      DefaultActionGroup group = new DefaultActionGroup(new ComputeDominatorAction(mySnapshot, this, myProject));
      myParentContainer.addTab(new TabInfo(splitter).setText(model.getHeapName()).setActions(group, ActionPlaces.UNKNOWN));
    }
  }
}
