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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.TargetArtifactsTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseCollapseAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseExpandAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.treeStructure.Tree;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.setUp;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;

public class TargetArtifactsPanel extends ToolWindowPanel {
  @NotNull private final Tree myTree;
  @NotNull private final TargetArtifactsTreeBuilder myTreeBuilder;
  @NotNull private final PsContext myContext;
  @NotNull private final NodeHyperlinkSupport<ModuleDependencyNode> myHyperlinkSupport;

  public TargetArtifactsPanel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super("Target Artifacts", AndroidIcons.Variant, ToolWindowAnchor.RIGHT);
    myContext = context;

    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(treeModel) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          ModuleDependencyNode node = myHyperlinkSupport.getIfHyperlink(e);
          if (node != null) {
            PsModuleDependency moduleDependency = node.getModels().get(0);
            String name = moduleDependency.getName();
            myContext.setSelectedModule(name, TargetArtifactsPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    setHeaderActions();
    getHeader().setPreferredFocusedComponent(myTree);

    myTreeBuilder = new TargetArtifactsTreeBuilder(module, myTree, treeModel);
    JScrollPane scrollPane = setUp(myTreeBuilder);
    add(scrollPane, BorderLayout.CENTER);

    myHyperlinkSupport = new NodeHyperlinkSupport<>(myTree, ModuleDependencyNode.class, myContext, false);
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

  public void displayTargetArtifacts(@Nullable PsAndroidDependency dependency) {
    myTreeBuilder.displayTargetArtifacts(dependency);
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myTreeBuilder);
    Disposer.dispose(myHyperlinkSupport);
  }
}
