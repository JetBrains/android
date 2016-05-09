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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.resolved;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleLibraryDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.resolved.treeview.ResolvedDependenciesTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.*;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.issues.SingleModuleIssuesRenderer;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseCollapseAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseExpandAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.setUp;
import static com.intellij.icons.AllIcons.Actions.Collapseall;
import static com.intellij.icons.AllIcons.Actions.Expandall;
import static com.intellij.util.ui.tree.TreeUtil.ensureSelection;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;

class ResolvedDependenciesPanel extends AbstractDependenciesPanel {
  @NotNull private final Tree myTree;
  @NotNull private final ResolvedDependenciesTreeBuilder myTreeBuilder;
  @NotNull private final PsContext myContext;
  @NotNull private final NodeHyperlinkSupport<ModuleDependencyNode> myHyperlinkSupport;

  ResolvedDependenciesPanel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super("Resolved Dependencies", context, module);
    myContext = context;

    initializeDependencyDetails();

    setIssuesViewer(new IssuesViewer(myContext, new SingleModuleIssuesRenderer()));

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
            myContext.setSelectedModule(name, ResolvedDependenciesPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    getContentsPanel().add(createActionsPanel(), BorderLayout.NORTH);

    myTreeBuilder = new ResolvedDependenciesTreeBuilder(module, myTree, treeModel);

    module.add(event -> myTreeBuilder.reset(), this);

    JScrollPane scrollPane = setUp(myTreeBuilder);
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    TreeSelectionListener treeSelectionListener = e -> {
      List<AbstractDependencyNode<? extends PsAndroidDependency>> selection = getMatchingSelection();
      PsAndroidDependency selected = !selection.isEmpty() ? selection.get(0).getModels().get(0) : null;

      updateDetails(selected);
      updateIssues(selection);
    };
    myTree.addTreeSelectionListener(treeSelectionListener);
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(x, y);
      }
    });

    myTreeBuilder.getInitialized().doWhenDone(this::doEnsureSelection);

    myHyperlinkSupport = new NodeHyperlinkSupport<>(myTree, ModuleDependencyNode.class, myContext, true);
  }

  private void initializeDependencyDetails() {
    addDetails(new ModuleLibraryDependencyDetails());
    addDetails(new ModuleDependencyDetails(getContext(), false));
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private List<AbstractDependencyNode<? extends PsAndroidDependency>> getMatchingSelection() {
    List<AbstractDependencyNode<? extends PsAndroidDependency>> selection = Lists.newArrayList();
    List<AbstractDependencyNode> matchingSelection = myTreeBuilder.getMatchingSelection(AbstractDependencyNode.class);
    for (AbstractDependencyNode node : matchingSelection) {
      selection.add(node);
    }
    return selection;
  }

  private void updateIssues(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selection) {
    List<PsIssue> issues = Lists.newArrayList();
    for (AbstractDependencyNode<? extends PsAndroidDependency> node : selection) {
      for (PsAndroidDependency dependency : node.getModels()) {
        issues.addAll(myContext.getAnalyzerDaemon().getIssues().findIssues(dependency, null));
      }
    }
    displayIssues(issues);
  }

  @Override
  @NotNull
  protected List<AnAction> getExtraToolbarActions() {
    List<AnAction> actions = Lists.newArrayList();

    actions.add(new SelectNodesMatchingCurrentSelectionAction() {
      @Override
      @NotNull
      protected AbstractPsNodeTreeBuilder getTreeBuilder() {
        return myTreeBuilder;
      }
    });
    actions.add(new Separator());

    actions.add(new AbstractBaseExpandAllAction(myTree, Expandall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTree.requestFocusInWindow();
        myTreeBuilder.expandAllNodes();
        doEnsureSelection();
      }
    });

    actions.add(new AbstractBaseCollapseAllAction(myTree, Collapseall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTreeBuilder.clearSelection();

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

  private void popupInvoked(int x, int y) {
    ModuleDependencyNode node = myHyperlinkSupport.getNodeForLocation(x, y);

    if (node != null) {
      PsModuleDependency moduleDependency = node.getModels().get(0);

      String name = moduleDependency.getName();
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new GoToModuleAction(name, myContext, myTree));

      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
      popupMenu.getComponent().show(myTree, x, y);
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(myTreeBuilder);
    Disposer.dispose(myHyperlinkSupport);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return null;
  }

  @Override
  public void queryPlace(@NotNull Place place) {

  }
}
