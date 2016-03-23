/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview.TargetModelsTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.setUp;

class TargetModulesPanel extends ToolWindowPanel {
  @NotNull private final Tree myTree;
  @NotNull private final TargetModelsTreeBuilder myTreeBuilder;

  TargetModulesPanel(@NotNull PsProject project) {
    super("Target Modules", AllIcons.Nodes.ModuleGroup, null);
    setHeaderActions();

    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(treeModel);

    getHeader().setPreferredFocusedComponent(myTree);

    myTreeBuilder = new TargetModelsTreeBuilder(project, myTree, treeModel);

    JScrollPane scrollPane = setUp(myTree);
    add(scrollPane, BorderLayout.CENTER);
  }

  private void setHeaderActions() {
    List<AnAction> additionalActions = Lists.newArrayList();
    additionalActions.add(new DumbAwareAction("Expand All", "", AllIcons.General.ExpandAll) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTree.requestFocusInWindow();
        myTreeBuilder.expandAllNodes();
      }
    });

    additionalActions.add(new DumbAwareAction("Collapse All", "", AllIcons.General.CollapseAll) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTree.requestFocusInWindow();
        myTreeBuilder.collapseAllNodes();
      }
    });

    getHeader().setAdditionalActions(additionalActions);
  }

  void displayTargetModules(@NotNull List<? extends PsAndroidDependency> dependencies) {
    myTreeBuilder.displayTargetModules(dependencies);
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myTreeBuilder);
  }
}
