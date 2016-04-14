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

import com.android.tools.idea.editors.hprof.views.ClassesTreeView;
import com.android.tools.idea.editors.hprof.views.GoToInstanceListener;
import com.android.tools.idea.editors.hprof.views.InstanceReferenceTreeView;
import com.android.tools.idea.editors.hprof.views.InstancesTreeView;
import com.android.tools.idea.editors.hprof.views.SelectionModel;
import com.android.tools.perflib.heap.*;
import com.intellij.designer.LightFillLayout;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class HprofView implements Disposable {
  public static final String TOOLBAR_NAME = "HprofActionToolbar";

  private static final int DIVIDER_WIDTH = 4;
  @NotNull private JPanel myContainer;
  private Snapshot mySnapshot;
  @SuppressWarnings("NullableProblems") @NotNull private SelectionModel mySelectionModel;

  public HprofView(@NotNull Project project, @NotNull HprofEditor editor, @NotNull Snapshot snapshot) {
    JBPanel treePanel = new JBPanel(new BorderLayout());
    treePanel.setBackground(JBColor.background());

    mySnapshot = snapshot;

    assert (mySnapshot.getHeaps().size() > 0);
    Heap currentHeap = null;
    for (Heap heap : mySnapshot.getHeaps()) {
      if ("app".equals(heap.getName())) {
        currentHeap = heap;
        break;
      }
      else if (currentHeap == null) {
        currentHeap = heap;
      }
    }

    if (currentHeap == null) {
      editor.setInvalid();
      // TODO: Add a simple panel to show that the hprof file is invalid.
      throw new IllegalStateException("Invalid heap given to HprofViewPanel.");
    }
    mySelectionModel = new SelectionModel(currentHeap);
    Disposer.register(this, mySelectionModel);

    DefaultActionGroup group = new DefaultActionGroup(new ComboBoxAction() {
      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        assert mySnapshot != null;
        for (final Heap heap : mySnapshot.getHeaps()) {
          if ("default".equals(heap.getName()) && heap.getClasses().isEmpty() && heap.getInstancesCount() == 0) {
            continue;
          }
          group.add(new AnAction(StringUtil.capitalize(heap.getName() + " heap")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              mySelectionModel.setHeap(heap);
            }
          });
        }
        return group;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        if (mySnapshot != null) { // Perform a check since the IDE sometimes updates the buttons after the view/snapshot is disposed.
          getTemplatePresentation().setText(StringUtil.capitalize(mySelectionModel.getHeap().getName() + " heap"));
          e.getPresentation().setText(StringUtil.capitalize(mySelectionModel.getHeap().getName() + " heap"));
        }
      }
    });

    final InstanceReferenceTreeView referenceTree = new InstanceReferenceTreeView(project, mySelectionModel);
    treePanel.add(referenceTree.getComponent(), BorderLayout.CENTER);

    final InstancesTreeView instancesTreeView = new InstancesTreeView(project, mySelectionModel);
    Disposer.register(this, instancesTreeView);

    final ClassesTreeView classesTreeView = new ClassesTreeView(project, group, mySelectionModel);
    JBSplitter splitter = createNavigationSplitter(classesTreeView.getComponent(), instancesTreeView.getComponent());
    Disposer.register(this, classesTreeView);

    GoToInstanceListener goToInstanceListener = new GoToInstanceListener() {
      @Override
      public void goToInstance(@NotNull Instance instance) {
        ClassObj classObj = instance instanceof ClassObj ? (ClassObj)instance : instance.getClassObj();
        mySelectionModel.setHeap(instance.getHeap());
        mySelectionModel.setClassObj(classObj);
        classesTreeView.requestFocus();
        if (instance instanceof ClassInstance || instance instanceof ArrayInstance) {
          mySelectionModel.setInstance(instance);
          instancesTreeView.requestFocus();
        }
      }
    };
    referenceTree.addGoToInstanceListener(goToInstanceListener);
    instancesTreeView.addGoToInstanceListener(goToInstanceListener);

    JBPanel classPanel = new JBPanel(new BorderLayout());
    classPanel.add(splitter, BorderLayout.CENTER);

    JBSplitter mainSplitter = new JBSplitter(true);
    mainSplitter.setFirstComponent(classPanel);
    mainSplitter.setSecondComponent(treePanel);
    mainSplitter.setDividerWidth(DIVIDER_WIDTH);

    myContainer = new JPanel(new LightFillLayout());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    toolbar.getComponent().setName(TOOLBAR_NAME);
    myContainer.add(toolbar.getComponent(), BorderLayout.NORTH);
    myContainer.add(mainSplitter);
  }

  @NotNull
  public static JBSplitter createNavigationSplitter(@Nullable JComponent leftPanelContents, @Nullable JComponent rightPanelContents) {
    JBPanel navigationPanel = new JBPanel(new BorderLayout());
    navigationPanel.setBackground(JBColor.background());
    if (leftPanelContents != null) {
      navigationPanel.add(leftPanelContents, BorderLayout.CENTER);
    }

    JBPanel contextInformationPanel = new JBPanel(new BorderLayout());
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

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @Override
  public void dispose() {
    myContainer.removeAll();
    mySnapshot = null;
  }
}
