/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.dependencies.module;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.setUp;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.DependencySelection;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.GoToModuleAction;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.ResolvedDependenciesTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseCollapseAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsModuleDependency;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.swing.JScrollPane;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResolvedDependenciesPanel extends ToolWindowPanel implements DependencySelection {
  @NotNull private final Tree myTree;
  @NotNull private final ResolvedDependenciesTreeBuilder myTreeBuilder;
  @NotNull private final PsContext myContext;
  @NotNull private final NodeHyperlinkSupport<ModuleDependencyNode> myHyperlinkSupport;

  @NotNull private final SelectionChangeEventDispatcher<PsBaseDependency> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  private boolean myIgnoreTreeSelectionEvents;

  public ResolvedDependenciesPanel(@NotNull PsModule module,
                                   @NotNull PsContext context,
                                   @NotNull DependencySelection dependencySelection) {
    this("Resolved Dependencies", module, context, dependencySelection, ToolWindowAnchor.RIGHT);
  }

  private ResolvedDependenciesPanel(@NotNull String title,
                                   @NotNull PsModule module,
                                   @NotNull PsContext context,
                                   @NotNull DependencySelection dependencySelection,
                                   @Nullable ToolWindowAnchor anchor) {
    super(title, StudioIcons.Misc.PROJECT_SYSTEM_VARIANT, anchor);
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
            myContext.setSelectedModule(name, ResolvedDependenciesPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };
    myTree.setRowHeight(JBUI.scale(24));

    setHeaderActions();
    getHeader().setPreferredFocusedComponent(myTree);

    myTreeBuilder = new ResolvedDependenciesTreeBuilder(
      module, myTree, treeModel, myContext.getUiSettings());

    module.add(event -> myTreeBuilder.reset(), this);

    JScrollPane scrollPane = setUp(myTreeBuilder, "resolvedDependencies");
    add(scrollPane, BorderLayout.CENTER);

    TreeSelectionListener treeSelectionListener = e -> {
      if (myIgnoreTreeSelectionEvents) {
        return;
      }

      PsBaseDependency selected = getSelection();
      if (selected == null) {
        AbstractPsModelNode selectedNode = getSelectionIfSingle();
        if (selectedNode != null && !(selectedNode instanceof AbstractDependencyNode)) {
          // A non-dependency node was selected (e.g. a variant/artifact node)
          notifySelectionChanged(null);
        }
      }
      else {
        notifySelectionChanged(selected);
      }
    };
    myTree.addTreeSelectionListener(treeSelectionListener);
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(x, y);
      }
    });

    myHyperlinkSupport = new NodeHyperlinkSupport<>(myTree, ModuleDependencyNode.class, myContext, false);
  }

  private void notifySelectionChanged(@Nullable PsBaseDependency selected) {
    myEventDispatcher.selectionChanged(selected);
  }

  private void setHeaderActions() {
    List<AnAction> additionalActions = new ArrayList<>();

    additionalActions.add(new AbstractBaseCollapseAllAction(myTree) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        collapseAllNodes();
      }
    });

    additionalActions.add(Separator.getInstance());
    getHeader().setAdditionalActions(additionalActions);
  }

  private void collapseAllNodes() {
    myTree.requestFocusInWindow();

    myIgnoreTreeSelectionEvents = true;
    myTreeBuilder.collapseAllNodes();
    myIgnoreTreeSelectionEvents = false;
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
  public ActionCallback setSelection(@Nullable Collection<PsBaseDependency> selection) {
    if (selection == null || selection.isEmpty()) {
      myTreeBuilder.clearSelection();
    }
    return ActionCallback.DONE;
  }

  public void add(@NotNull SelectionChangeListener<PsBaseDependency> listener) {
    myEventDispatcher.addListener(listener, this);
  }

  @Override
  @Nullable
  public PsBaseDependency getSelection() {
    AbstractPsModelNode selection = getSelectionIfSingle();
    if (selection instanceof AbstractDependencyNode) {
      AbstractDependencyNode node = (AbstractDependencyNode)selection;
      List<?> models = node.getModels();
      if (!models.isEmpty()) {
        return (PsBaseDependency)models.get(0);
      }
    }
    return null;
  }

  @Nullable
  private AbstractPsModelNode getSelectionIfSingle() {
    Set<AbstractPsModelNode> selection = myTreeBuilder.getSelectedElements(AbstractPsModelNode.class);
    if (selection.size() == 1) {
      return getFirstItem(selection);
    }
    return null;
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myTreeBuilder);
    Disposer.dispose(myHyperlinkSupport);
  }
}
