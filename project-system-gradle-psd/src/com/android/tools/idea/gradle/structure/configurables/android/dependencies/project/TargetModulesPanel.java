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

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.setUp;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;

import com.android.tools.idea.gradle.AndroidGradlePsdBundle;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview.TargetAndroidModuleNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview.TargetModulesTreeStructure;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.GoToModuleAction;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseCollapseAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;

class TargetModulesPanel extends ToolWindowPanel {
  @NotNull private final PsContext myContext;
  @NotNull private final Tree myTree;
  @NotNull private final StructureTreeModel<TargetModulesTreeStructure> structureTreeModel;
  @NotNull private final NodeHyperlinkSupport<TargetAndroidModuleNode> myHyperlinkSupport;

  TargetModulesPanel(@NotNull PsContext context) {
    super(AndroidGradlePsdBundle.message("tab.title.target.modules.artifacts"), AllIcons.Nodes.ModuleGroup, ToolWindowAnchor.RIGHT);
    myContext = context;

    TargetModulesTreeStructure treeStructure = new TargetModulesTreeStructure(context.getUiSettings());
    structureTreeModel = new StructureTreeModel<>(treeStructure, this);
    myTree = new Tree(new AsyncTreeModel(structureTreeModel, this)) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          TargetAndroidModuleNode node = myHyperlinkSupport.getIfHyperlink(e);
          if (node != null) {
            PsAndroidModule module = node.getFirstModel();
            String name = module.getName();
            myContext.setSelectedModule(name, TargetModulesPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };
    myTree.setRowHeight(JBUI.scale(24));
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(x, y);
      }
    });

    setHeaderActions();
    getHeader().setPreferredFocusedComponent(myTree);

    JScrollPane scrollPane = setUp(myTree, "targetModules");
    add(scrollPane, BorderLayout.CENTER);

    myHyperlinkSupport = new NodeHyperlinkSupport<>(myTree, TargetAndroidModuleNode.class, myContext, false);
  }

  private void setHeaderActions() {
    List<AnAction> additionalActions = new ArrayList<>();
    additionalActions.add(new AbstractBaseCollapseAllAction(myTree) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myTree.requestFocusInWindow();
        TreeUtil.collapseAll(myTree, -1);
      }
    });

    getHeader().setAdditionalActions(additionalActions);
  }

  private void popupInvoked(int x, int y) {
    TargetAndroidModuleNode node = myHyperlinkSupport.getNodeForLocation(x, y);

    if (node != null) {
      PsAndroidModule module = node.getFirstModel();

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new GoToModuleAction(module.getName(), myContext, myTree));

      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
      popupMenu.getComponent().show(myTree, x, y);
    }
  }

  void displayTargetModules(@NotNull List<AbstractDependencyNode<?, ? extends PsBaseDependency>> dependencyNodes) {
    structureTreeModel.getTreeStructure().displayTargetModules(dependencyNodes.stream().map(AbstractDependencyNode::getModels).toList());
    structureTreeModel.invalidateAsync();
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myHyperlinkSupport);
  }
}
