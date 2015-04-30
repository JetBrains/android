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
package com.android.tools.idea.editors.hprof;

import com.android.tools.idea.editors.hprof.descriptors.ContainerDescriptorImpl;
import com.android.tools.idea.editors.hprof.tables.classtable.ClassTable;
import com.android.tools.idea.editors.hprof.tables.classtable.ClassTableModel;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstancesDebuggerTree;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.Wrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HprofViewPanel implements Disposable {
  private static final int DIVIDER_WIDTH = 4;
  @NotNull private Project myProject;
  @NotNull private JPanel myContainer;
  @NotNull private JPanel myDominatorPanel;

  public HprofViewPanel(@NotNull final Project project, @NotNull Snapshot snapshot) {
    myProject = project;

    InstancesDebuggerTree instancesDebuggerTree = createInstancesDebuggerTree();
    final ClassTable classTable = createClassTable(snapshot, instancesDebuggerTree);
    JBSplitter splitter = createNavigationSplitter(classTable, instancesDebuggerTree.getComponent());

    myDominatorPanel = new JPanel(new BorderLayout());
    myDominatorPanel.setBorder(new EmptyBorder(0, 2, 0, 0));

    JBPanel treePanel = new JBPanel(new BorderLayout());
    treePanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    treePanel.setBackground(JBColor.background());
    treePanel.add(myDominatorPanel, BorderLayout.CENTER);

    Wrapper treePanelWrapper = new Wrapper(treePanel);
    treePanelWrapper.setBorder(new EmptyBorder(0, 1, 0, 0));

    JBPanel classPanel = new JBPanel(new BorderLayout());
    classPanel.add(splitter, BorderLayout.CENTER);

    DefaultActionGroup group = new DefaultActionGroup(new ComputeDominatorAction(snapshot, myProject) {
      @Override
      public void onDominatorsComputed() {
        // TODO this should be done with tables adding listeners to the snapshot, as it's the snapshot that changes.
        classTable.notifyDominatorsComputed();
      }
    });
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    classPanel.add(toolbar.getComponent(), BorderLayout.NORTH);

    JBSplitter mainSplitter = new JBSplitter(true);
    mainSplitter.setFirstComponent(classPanel);
    mainSplitter.setSecondComponent(treePanelWrapper);
    mainSplitter.setDividerWidth(DIVIDER_WIDTH);

    myContainer = new JPanel(new BorderLayout());
    myContainer.add(mainSplitter);
  }

  @NotNull
  private static ClassTable createClassTable(@NotNull Snapshot snapshot, @NotNull final InstancesDebuggerTree instancesDebuggerTree) {
    final ClassTable classTable = new ClassTable(new ClassTableModel(snapshot));
    classTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent mouseEvent) {
        super.mouseReleased(mouseEvent);
        int row = classTable.getSelectedRow();
        if (row >= 0) {
          int modelRow = classTable.getRowSorter().convertRowIndexToModel(row);
          ClassObj classObj = (ClassObj)classTable.getModel().getValueAt(modelRow, 0);
          instancesDebuggerTree.setRoot(DebuggerTreeNodeImpl.createNodeNoUpdate(instancesDebuggerTree.getComponent(),
                                                                                new ContainerDescriptorImpl(classObj.getInstances())));

        }
      }
    });
    return classTable;
  }

  @NotNull
  private InstancesDebuggerTree createInstancesDebuggerTree() {
    return new InstancesDebuggerTree(myProject);
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

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  @Override
  public void dispose() {

  }
}
