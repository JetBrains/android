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

import com.android.tools.idea.editors.hprof.tables.InstanceReferenceTree;
import com.android.tools.idea.editors.hprof.tables.InstancesTree;
import com.android.tools.idea.editors.hprof.tables.classtable.ClassTable;
import com.android.tools.idea.editors.hprof.tables.classtable.ClassTableModel;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HprofViewPanel implements Disposable {
  private static final int DIVIDER_WIDTH = 4;
  @NotNull private JPanel myContainer;
  private Heap myCurrentHeap = null;

  public HprofViewPanel(@NotNull final Project project, @NotNull final Snapshot snapshot) {
    JBPanel treePanel = new JBPanel(new BorderLayout());
    treePanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    treePanel.setBackground(JBColor.background());

    final InstanceReferenceTree referenceTree = new InstanceReferenceTree();
    treePanel.add(referenceTree.getComponent(), BorderLayout.CENTER);

    assert (snapshot.getHeaps().size() > 0);
    for (Heap heap : snapshot.getHeaps()) {
      if ("app".equals(heap.getName())) {
        myCurrentHeap = heap;
        break;
      }
      else if (myCurrentHeap == null) {
        myCurrentHeap = heap;
      }
    }

    final InstancesTree instancesTree = new InstancesTree(project, myCurrentHeap, referenceTree.getOnInstanceSelectionListener());
    final ClassTable classTable = createClassTable(instancesTree);
    JBScrollPane classTableScrollPane = new JBScrollPane();
    classTableScrollPane.setViewportView(classTable);
    JBSplitter splitter = createNavigationSplitter(classTableScrollPane, instancesTree.getComponent());

    JBPanel classPanel = new JBPanel(new BorderLayout());
    classPanel.add(splitter, BorderLayout.CENTER);

    DefaultActionGroup group = new DefaultActionGroup(new ComboBoxAction() {
      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        for (final Heap heap : snapshot.getHeaps()) {
          group.add(new AnAction(heap.getName() + " heap") {
            @Override
            public void actionPerformed(AnActionEvent e) {
              myCurrentHeap = heap;
              classTable.setHeap(heap, instancesTree.getClassObj());
              instancesTree.setClassObj(heap, instancesTree.getClassObj());
              referenceTree.clearInstance();
            }
          });
        }
        return group;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        getTemplatePresentation().setText(myCurrentHeap.getName() + " heap");
        e.getPresentation().setText(myCurrentHeap.getName() + " heap");
      }
    }, new ComputeDominatorAction(snapshot, project) {
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
    mainSplitter.setSecondComponent(treePanel);
    mainSplitter.setDividerWidth(DIVIDER_WIDTH);

    myContainer = new JPanel(new BorderLayout());
    myContainer.add(mainSplitter);
  }

  @NotNull
  private ClassTable createClassTable(@NotNull final InstancesTree instancesTree) {
    final ClassTable classTable = new ClassTable(new ClassTableModel(myCurrentHeap));
    classTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent mouseEvent) {
        super.mousePressed(mouseEvent);
        int row = classTable.getSelectedRow();
        if (row >= 0) {
          int modelRow = classTable.getRowSorter().convertRowIndexToModel(row);
          ClassObj classObj = (ClassObj)classTable.getModel().getValueAt(modelRow, 0);
          instancesTree.setClassObj(myCurrentHeap, classObj);
        }
      }
    });
    return classTable;
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
      contextInformationPanel.add(rightPanelContents, BorderLayout.CENTER);
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
