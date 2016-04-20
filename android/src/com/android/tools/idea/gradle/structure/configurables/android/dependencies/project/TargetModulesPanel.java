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

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview.TargetAndroidModuleNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview.TargetModulesTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.*;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseCollapseAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseExpandAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.setUp;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;

class TargetModulesPanel extends ToolWindowPanel {
  @NotNull private final PsContext myContext;
  @NotNull private final Tree myTree;
  @NotNull private final TargetModulesTreeBuilder myTreeBuilder;
  @NotNull private final NodeHyperlinkSupport<TargetAndroidModuleNode> myHyperlinkSupport;

  TargetModulesPanel(@NotNull PsContext context) {
    super("Target Modules", AllIcons.Nodes.ModuleGroup, null);
    myContext = context;

    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(treeModel) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          TargetAndroidModuleNode node = myHyperlinkSupport.getIfHyperlink(e);
          if (node != null) {
            PsAndroidModule module = node.getModels().get(0);
            String name = module.getName();
            myContext.setSelectedModule(name, TargetModulesPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(x, y);
      }
    });

    setHeaderActions();
    getHeader().setPreferredFocusedComponent(myTree);

    myTreeBuilder = new TargetModulesTreeBuilder(myContext.getProject(), myTree, treeModel);

    JScrollPane scrollPane = setUp(myTreeBuilder);
    add(scrollPane, BorderLayout.CENTER);

    myHyperlinkSupport = new NodeHyperlinkSupport<>(myTree, TargetAndroidModuleNode.class, myContext, false);
  }

  private void setHeaderActions() {
    List<AnAction> additionalActions = Lists.newArrayList();
    additionalActions.add(new AbstractBaseExpandAllAction(myTree) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTree.requestFocusInWindow();
        myTreeBuilder.expandAllNodes();
      }
    });

    additionalActions.add(new AbstractBaseCollapseAllAction(myTree) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTree.requestFocusInWindow();
        myTreeBuilder.collapseAllNodes();
      }
    });

    getHeader().setAdditionalActions(additionalActions);
  }

  private void popupInvoked(int x, int y) {
    TargetAndroidModuleNode node = myHyperlinkSupport.getNodeForLocation(x, y);

    if (node != null) {
      PsAndroidModule module = node.getModels().get(0);

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new GoToModuleAction(module.getName(), myContext, myTree));

      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
      popupMenu.getComponent().show(myTree, x, y);
    }
  }

  void displayTargetModules(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> dependencyNodes) {
    myTreeBuilder.displayTargetModules(dependencyNodes);
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myTreeBuilder);
    Disposer.dispose(myHyperlinkSupport);
  }
}
