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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencySelection;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.VariantsTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.treeStructure.Tree;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.wm.impl.content.ToolWindowContentUi.POPUP_PLACE;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

class VariantsToolWindowPanel extends ToolWindowPanel implements DependencySelection {
  @NotNull private final Tree myTree;
  @NotNull private final VariantsTreeBuilder myTreeBuilder;
  @NotNull private final TreeSelectionListener myTreeSelectionListener;

  @NotNull private final List<SelectionListener> mySelectionListeners = Lists.newCopyOnWriteArrayList();

  VariantsToolWindowPanel(@NotNull PsdAndroidModuleModel moduleModel, @NotNull DependencySelection dependencySelection) {
    super("Resolved Dependencies", AndroidIcons.Variant, ToolWindowAnchor.RIGHT);
    setHeaderActions();

    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree = new Tree(treeModel);
    myTree.setExpandsSelectedPaths(true);
    myTree.setRootVisible(false);
    getHeader().setPreferredFocusedComponent(myTree);

    TreeSelectionModel selectionModel = myTree.getSelectionModel();
    selectionModel.setSelectionMode(DISCONTIGUOUS_TREE_SELECTION);

    myTreeBuilder = new VariantsTreeBuilder(moduleModel, myTree, treeModel, dependencySelection, this);

    JScrollPane scrollPane = createScrollPane(myTree);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    add(scrollPane, BorderLayout.CENTER);

    myTreeSelectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        PsdAndroidDependencyModel selected = getSelection();
        if (selected != null) {
          setSelection(selected);
          for (SelectionListener listener : mySelectionListeners) {
            listener.dependencyModelSelected(selected);
          }
        }
      }
    };
    myTree.addTreeSelectionListener(myTreeSelectionListener);
  }

  private void setHeaderActions() {
    final DefaultActionGroup settingsGroup = new DefaultActionGroup();

    settingsGroup.add(new ToggleAction("Group Variants") {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(false); // TODO: Fix "Group Variants" functionality.
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return PsdUISettings.getInstance().VARIANTS_DEPENDENCIES_GROUP_VARIANTS;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        PsdUISettings settings = PsdUISettings.getInstance();
        if (settings.VARIANTS_DEPENDENCIES_GROUP_VARIANTS != state) {
          settings.VARIANTS_DEPENDENCIES_GROUP_VARIANTS = state;
          settings.fireUISettingsChanged();
        }
      }
    });

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
        collapseAllNodes();
      }
    });

    additionalActions.add(Separator.getInstance());

    additionalActions.add(new DumbAwareAction("", "", AllIcons.General.Gear) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        InputEvent inputEvent = e.getInputEvent();
        ActionManagerImpl actionManager = (ActionManagerImpl)ActionManager.getInstance();
        ActionPopupMenu popupMenu =
          actionManager.createActionPopupMenu(POPUP_PLACE, settingsGroup, new MenuItemPresentationFactory(true));
        int x = 0;
        int y = 0;
        if (inputEvent instanceof MouseEvent) {
          x = ((MouseEvent)inputEvent).getX();
          y = ((MouseEvent)inputEvent).getY();
        }
        popupMenu.getComponent().show(inputEvent.getComponent(), x, y);
      }
    });

    getHeader().setAdditionalActions(additionalActions);
  }

  private void collapseAllNodes() {
    myTree.requestFocusInWindow();

    // Remove selection listener because the selection changes when collapsing all nodes and the tree will try to use the previously
    // selected dependency, expanding nodes while restoring the selection.
    myTree.removeTreeSelectionListener(myTreeSelectionListener);

    myTreeBuilder.collapseAllNodes();
    myTree.addTreeSelectionListener(myTreeSelectionListener);
  }

  @Override
  public void setSelection(@NotNull PsdAndroidDependencyModel selection) {
    myTreeBuilder.setSelection(selection);
  }

  void add(@NotNull SelectionListener listener) {
    PsdAndroidDependencyModel selected = getSelection();
    if (selected != null) {
      listener.dependencyModelSelected(selected);
    }
    mySelectionListeners.add(listener);
  }

  @Override
  @Nullable
  public PsdAndroidDependencyModel getSelection() {
    Set<AbstractDependencyNode> selection = myTreeBuilder.getSelectedElements(AbstractDependencyNode.class);
    if (selection.size() == 1) {
      AbstractDependencyNode node = getFirstItem(selection);
      if (node != null) {
        List<?> models = node.getModels();
        if (!models.isEmpty()) {
          return (PsdAndroidDependencyModel)models.get(0);
        }
      }
    }
    return null;
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myTreeBuilder);
    mySelectionListeners.clear();
  }

  public interface SelectionListener {
    void dependencyModelSelected(@NotNull PsdAndroidDependencyModel model);
  }
}
