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
import static com.intellij.icons.AllIcons.Actions.Collapseall;
import static com.intellij.icons.AllIcons.Actions.Expandall;
import static com.intellij.util.ui.tree.TreeUtil.ensureSelection;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.JarDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.ModuleDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.MultipleLibraryDependenciesDetails;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.GoToModuleAction;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.graph.DependenciesTreeRootNode;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.graph.DependenciesTreeStructure;
import com.android.tools.idea.gradle.structure.configurables.issues.DependencyViewIssuesRenderer;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.AbstractDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseCollapseAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseExpandAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsModuleDependency;
import com.google.common.collect.Sets;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.event.TreeSelectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DependencyGraphPanel extends AbstractDependenciesPanel {
  @NotNull private final PsContext myContext;

  @NotNull private final Tree myTree;
  @NotNull private final StructureTreeModel<DependenciesTreeStructure> myStructureTreeModel;
  @NotNull private final NodeHyperlinkSupport<ModuleDependencyNode> myHyperlinkSupport;

  @NotNull private final SelectionChangeEventDispatcher<List<AbstractDependencyNode<?, ? extends PsBaseDependency>>> myEventDispatcher =
    new SelectionChangeEventDispatcher<>();

  private final MergingUpdateQueue myUpdateIssuesQueue = new MergingUpdateQueue("myUpdateIssuesQueue", 300, true, this, this);

  private boolean myIgnoreTreeSelectionEvents;

  DependencyGraphPanel(@NotNull PsModule fakeModule, @NotNull PsContext context) {
    super("All Dependencies", context, null);
    myContext = context;
    myUpdateIssuesQueue.setRestartTimerOnAdd(true);

    initializeDependencyDetails();

    setIssuesViewer(new IssuesViewer(myContext, new DependencyViewIssuesRenderer(myContext)));

    DependenciesTreeStructure treeStructure = new DependenciesTreeStructure(createRootNode());
    myStructureTreeModel = new StructureTreeModel<>(treeStructure, this);
    myTree = new Tree(new AsyncTreeModel(myStructureTreeModel, this)) {
      {
        setRowHeight(JBUI.scale(24));
      }
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          ModuleDependencyNode node = myHyperlinkSupport.getIfHyperlink(e);
          if (node != null) {
            PsModuleDependency moduleDependency = node.getFirstModel();
            String name = moduleDependency.getName();
            myContext.setSelectedModule(name, DependencyGraphPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    JScrollPane scrollPane = setUp(myTree, "dependenciesTree");
    getContentsPanel().add(createActionsPanel(), BorderLayout.NORTH);
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    TreeSelectionListener treeSelectionListener = e -> {
      if (!myIgnoreTreeSelectionEvents) {
        List<AbstractDependencyNode<?, ? extends PsBaseDependency>> selection = getSelection();
        PsBaseDependency selected = !selection.isEmpty() ? selection.get(0).getFirstModel() : null;
        if (selected == null) {
          notifySelectionChanged(Collections.emptyList());
        }
        else {
          notifySelectionChanged(selection);
        }

        updateDetails(selected);
        updateIssues(selection);
      }
      myIgnoreTreeSelectionEvents = false;
    };
    myTree.addTreeSelectionListener(treeSelectionListener);
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(x, y);
      }
    });

    doEnsureSelection();

    myHyperlinkSupport = new NodeHyperlinkSupport<>(myTree, ModuleDependencyNode.class, myContext, true);

    PsModule.DependenciesChangeListener dependenciesChangeListener = event -> {
      myStructureTreeModel.getInvoker().invoke(() -> myStructureTreeModel.getTreeStructure().reset());
      myStructureTreeModel.invalidateAsync().thenRun(() -> {
        if (event instanceof PsModule.DependencyAddedEvent) {
          // set the selection to the newly-added dependency
          PsDeclaredDependency dependency = ((PsModule.DependencyAddedEvent)event).getDependency().getValue();
          myStructureTreeModel.getInvoker()
            .compute(() -> myStructureTreeModel.getTreeStructure().findDeclaredDependencyNode(dependency))
            .onSuccess(nodeOptional -> nodeOptional.ifPresent(node -> myStructureTreeModel.select(node, myTree, path -> {})));
        }
      });
    };
    myContext.getProject().forEachModule(module -> module.add(dependenciesChangeListener, this));
  }

  @NotNull
  private DependenciesTreeRootNode createRootNode() {
    return new DependenciesTreeRootNode(
      myContext.getProject(),
      myContext.getUiSettings());
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private List<AbstractDependencyNode<?, ? extends PsBaseDependency>> getSelection() {
    List<AbstractDependencyNode<?, ? extends PsBaseDependency>> selection = new ArrayList<>();
    List<AbstractDependencyNode> matchingSelection = TreeUtil.collectSelectedObjectsOfType(myTree, AbstractDependencyNode.class);
    for (AbstractDependencyNode node : matchingSelection) {
      selection.add(node);
    }
    return selection;
  }

  private void updateIssues(@NotNull List<AbstractDependencyNode<?, ? extends PsBaseDependency>> selection) {
    myUpdateIssuesQueue.queue(new Update(this) {
      @Override
      public void run() {
        Set<PsIssue> issues = Sets.newHashSet();
        for (AbstractDependencyNode<?, ? extends PsBaseDependency> node : selection) {
          for (PsBaseDependency dependency : node.getModels()) {
            issues.addAll(myContext.getAnalyzerDaemon().getIssues().findIssues(dependency.getPath(), null));
          }
        }
        displayIssues(issues, null);
      }
    });
  }

  private void notifySelectionChanged(@NotNull List<AbstractDependencyNode<?, ? extends PsBaseDependency>> selected) {
    myEventDispatcher.selectionChanged(selected);
  }

  private void popupInvoked(int x, int y) {
    ModuleDependencyNode node = myHyperlinkSupport.getNodeForLocation(x, y);

    if (node != null) {
      PsModuleDependency moduleDependency = node.getFirstModel();

      String name = moduleDependency.getName();
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new GoToModuleAction(name, getContext(), myTree));

      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("DependencyGraphPanel", group);
      popupMenu.getComponent().show(myTree, x, y);
    }
  }

  private void initializeDependencyDetails() {
    addDetails(new MultipleLibraryDependenciesDetails());
    addDetails(new JarDependencyDetails(getContext(), false));
    addDetails(new ModuleDependencyDetails(getContext(), false));
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  void add(@NotNull SelectionChangeListener<List<AbstractDependencyNode<?, ? extends PsBaseDependency>>> listener) {
    myEventDispatcher.addListener(listener, this);
  }

  @Override
  @NotNull
  protected List<AnAction> getExtraToolbarActions(@NotNull JComponent focusComponent) {
    List<AnAction> actions = new ArrayList<>();

    actions.add(new AbstractBaseExpandAllAction(myTree, Expandall) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myTree.requestFocusInWindow();
        TreeUtil.expandAll(myTree);
        doEnsureSelection();
      }
    });

    actions.add(new AbstractBaseCollapseAllAction(myTree, Collapseall) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myTree.clearSelection();
        notifySelectionChanged(Collections.emptyList());

        myTree.requestFocusInWindow();
        TreeUtil.collapseAll(myTree, -1);
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
    Disposer.dispose(myHyperlinkSupport);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    // TODO implement
    return ActionCallback.DONE;
  }

  @Override
  @NotNull
  protected String getPlaceName() {
    return "dependencies.graph.project";
  }
}
