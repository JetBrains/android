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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDeclaredDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview.DeclaredDependenciesTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractBaseTreeBuilder.MatchingNodeCollector;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.GoToModuleAction;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.setUp;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.tree.TreeUtil.ensureSelection;

class DeclaredDependenciesPanel extends AbstractDeclaredDependenciesPanel {
  @NotNull private final Tree myTree;
  @NotNull private final DeclaredDependenciesTreeBuilder myTreeBuilder;
  @NotNull private final TreeSelectionListener myTreeSelectionListener;
  @NotNull private final NodeHyperlinkSupport<ModuleDependencyNode> myHyperlinkSupport;

  @NotNull private final EventDispatcher<SelectionListener> myEventDispatcher = EventDispatcher.create(SelectionListener.class);

  DeclaredDependenciesPanel(@NotNull PsProject project, @NotNull PsContext context) {
    super("All Dependencies", context, project, null);

    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(treeModel);

    JScrollPane scrollPane = setUp(myTree);
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    myTreeBuilder = new DeclaredDependenciesTreeBuilder(project, myTree, treeModel);

    myTreeSelectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final List<AbstractDependencyNode<? extends PsAndroidDependency>> selectedNodes = Lists.newArrayList();

        myTreeBuilder.updateSelection(new MatchingNodeCollector() {
          @Override
          protected void done(@NotNull List<AbstractPsdNode> matchingNodes) {
            for (AbstractPsdNode node : matchingNodes) {
              if (node instanceof AbstractDependencyNode) {
                selectedNodes.add(((AbstractDependencyNode<? extends PsAndroidDependency>)node));
              }
            }
          }
        });

        if (getSelection() != null) {
          notifySelectionChanged(selectedNodes);
        }
      }
    };
    myTree.addTreeSelectionListener(myTreeSelectionListener);
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(x, y);
      }
    });

    myTreeBuilder.getInitialized().doWhenDone(new Runnable() {
      @Override
      public void run() {
        doEnsureSelection();
      }
    });

    myHyperlinkSupport = new NodeHyperlinkSupport<ModuleDependencyNode>(myTree, ModuleDependencyNode.class);
  }

  @Nullable
  private AbstractDependencyNode<? extends PsAndroidDependency> getSelection() {
    Set<AbstractDependencyNode> selectedElements = myTreeBuilder.getSelectedElements(AbstractDependencyNode.class);
    if (selectedElements.size() == 1) {
      //noinspection unchecked
      return getFirstItem(selectedElements);
    }
    return null;
  }

  private void notifySelectionChanged(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selected) {
    myEventDispatcher.getMulticaster().dependencySelected(selected);
  }

  private void popupInvoked(int x, int y) {
    ModuleDependencyNode node = myHyperlinkSupport.getNodeForLocation(x, y);

    if (node != null) {
      PsModuleDependency moduleDependency = node.getModels().get(0);

      String name = moduleDependency.getName();
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new GoToModuleAction(name, getContext(), myTree));

      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
      popupMenu.getComponent().show(myTree, x, y);
    }
  }


  void add(@NotNull SelectionListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  @NotNull
  protected List<AnAction> getExtraToolbarActions() {
    List<AnAction> actions = Lists.newArrayList();

    actions.add(new DumbAwareAction("Expand All", null, AllIcons.Actions.Expandall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTree.requestFocusInWindow();
        myTreeBuilder.expandAllNodes();
        doEnsureSelection();
      }
    });

    actions.add(new DumbAwareAction("Collapse All", null, AllIcons.Actions.Collapseall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTreeBuilder.clearSelection();
        notifySelectionChanged(Collections.<AbstractDependencyNode<? extends PsAndroidDependency>>emptyList());

        myTree.requestFocusInWindow();
        myTreeBuilder.collapseAllNodes();
        doEnsureSelection();
      }
    });

    return actions;
  }

  private void doEnsureSelection() {
    ensureSelection(myTree);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myTreeBuilder);
    Disposer.dispose(myHyperlinkSupport);
  }

  public interface SelectionListener extends EventListener {
    void dependencySelected(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selectedNodes);
  }
}
