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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleLibraryDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.*;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.resolved.DependenciesTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.resolved.DependenciesTreeRootNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.resolved.DependenciesTreeStructure;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.issues.SingleModuleIssuesRenderer;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseCollapseAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseExpandAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.TreeVisitor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
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
import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.FOR_NAVIGATION;
import static com.intellij.icons.AllIcons.Actions.Collapseall;
import static com.intellij.icons.AllIcons.Actions.Expandall;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;

public class DependencyGraphPanel extends AbstractDependenciesPanel {
  @NotNull private final Tree myTree;
  @NotNull private final DependenciesTreeBuilder myTreeBuilder;
  @NotNull private final PsContext myContext;
  @NotNull private final String myPlaceName;
  @NotNull private final NodeHyperlinkSupport<ModuleDependencyNode> myHyperlinkSupport;

  @NotNull private final SelectionChangeEventDispatcher<AbstractDependencyNode<? extends PsAndroidDependency>> myEventDispatcher =
    new SelectionChangeEventDispatcher<>();

  public DependencyGraphPanel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super("Dependency Graph", context, module);
    myContext = context;

    myPlaceName = createPlaceName(module.getName());
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
            myContext.setSelectedModule(name, DependencyGraphPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    getContentsPanel().add(createActionsPanel(), BorderLayout.NORTH);

    DependenciesTreeStructure treeStructure = new DependenciesTreeStructure(createRootNode(module));
    myTreeBuilder = new DependenciesTreeBuilder(myTree, treeModel, treeStructure);

    module.add(event -> myTreeBuilder.reset(null), this);

    JScrollPane scrollPane = setUp(myTreeBuilder);
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    TreeSelectionListener treeSelectionListener = e -> {
      List<AbstractDependencyNode<? extends PsAndroidDependency>> selection = getMatchingSelection();
      notifySelectionChanged(selection.size() == 1 ? selection.get(0) : null);

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

    myTreeBuilder.getInitialized().doWhenDone(this::selectFirstNode);

    myHyperlinkSupport = new NodeHyperlinkSupport<>(myTree, ModuleDependencyNode.class, myContext, true);
  }

  @NotNull
  private static String createPlaceName(@NotNull String moduleName) {
    return "dependencies." + moduleName + ".place";
  }

  @NotNull
  private static DependenciesTreeRootNode<PsModule> createRootNode(@NotNull PsAndroidModule module) {
    return new DependenciesTreeRootNode<>(module, new DependenciesTreeRootNode.DependencyCollectorFunction<PsModule>() {
      @Override
      public DependenciesTreeRootNode.DependencyCollector apply(PsModule module) {
        DependenciesTreeRootNode.DependencyCollector collector = new DependenciesTreeRootNode.DependencyCollector();
        collectDeclaredDependencies(module, collector);
        return collector;
      }
    });
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
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  public void add(@NotNull SelectionChangeListener<AbstractDependencyNode<? extends PsAndroidDependency>> listener) {
    myEventDispatcher.addListener(listener, this);
  }

  @Override
  public void notifySelectionChanged() {
    myTreeBuilder.getInitialized().doWhenDone(() -> {
      List<AbstractDependencyNode<? extends PsAndroidDependency>> selection = getMatchingSelection();
      if (selection.isEmpty()) {
        selectFirstNode();
        selection = getMatchingSelection();
      }
      notifySelectionChanged(selection.size() == 1 ? selection.get(0) : null);
    });
  }

  private void notifySelectionChanged(@Nullable AbstractDependencyNode<? extends PsAndroidDependency> selected) {
    myEventDispatcher.selectionChanged(selected);
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
        selectFirstNode();
      }
    });

    actions.add(new AbstractBaseCollapseAllAction(myTree, Collapseall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTreeBuilder.clearSelection();

        myTree.requestFocusInWindow();
        myTreeBuilder.collapseAllNodes();
        selectFirstNode();
      }
    });

    return actions;
  }

  private void selectFirstNode() {
    Ref<AbstractDependencyNode> nodeRef = new Ref<>();
    myTreeBuilder.accept(AbstractDependencyNode.class, new TreeVisitor<AbstractDependencyNode>() {
      @Override
      public boolean visit(@NotNull AbstractDependencyNode node) {
        nodeRef.set(node);
        return true;
      }
    });
    myTreeBuilder.select(nodeRef.get());
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
    if (place != null) {
      Object path = place.getPath(myPlaceName);
      if (path instanceof String) {
        String pathText = (String)path;
        myTree.requestFocusInWindow();
        if (!pathText.isEmpty()) {
          Ref<AbstractDependencyNode> nodeRef = new Ref<>();
          myTreeBuilder.accept(AbstractDependencyNode.class, new TreeVisitor<AbstractDependencyNode>() {
            @Override
            public boolean visit(@NotNull AbstractDependencyNode node) {
              PsAndroidDependency dependency = (PsAndroidDependency)node.getModels().get(0);
              if (!(node.getParent() instanceof AbstractDependencyNode)) {
                // Only consider top-level dependencies (i.e. "declared" dependencies.
                String dependencyAsText = dependency.toText(FOR_NAVIGATION);
                if (pathText.equals(dependencyAsText)) {
                  nodeRef.set(node);
                  return true;
                }
              }
              return false;
            }
          });
          if (nodeRef.get() != null) {
            myTreeBuilder.select(nodeRef.get());
          }
        }
      }
    }
    return ActionCallback.DONE;
  }

  @Override
  @NotNull
  public String getPlaceName() {
    return myPlaceName;
  }
}
