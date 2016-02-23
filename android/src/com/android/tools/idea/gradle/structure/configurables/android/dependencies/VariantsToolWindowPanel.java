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
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
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
import javax.swing.tree.TreePath;
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
    super("Variants", AndroidIcons.Variant, ToolWindowAnchor.RIGHT);
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
        myTreeBuilder.expand();
      }
    });

    additionalActions.add(new DumbAwareAction("Collapse All", "", AllIcons.General.CollapseAll) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTreeBuilder.collapse();
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

  @Override
  public void setSelection(@NotNull final PsdAndroidDependencyModel selection) {
    myTreeBuilder.getInitialized().doWhenDone(new Runnable() {
      @Override
      public void run() {
        DefaultMutableTreeNode rootNode = myTreeBuilder.getRootNode();
        if (rootNode != null) {
          List<TreePath> selectionPaths = Lists.newArrayList();

          int variantCount = rootNode.getChildCount();
          for (int i = 0; i < variantCount; i++) {
            DefaultMutableTreeNode variantNode = (DefaultMutableTreeNode)rootNode.getChildAt(i);
            collectMatching(selection, variantNode, selectionPaths);
          }
          updateSelection(selectionPaths);
        }
      }
    });
  }

  private static void collectMatching(@NotNull PsdAndroidDependencyModel dependencyModel,
                                      @NotNull DefaultMutableTreeNode parentNode,
                                      @NotNull List<TreePath> selectionPaths) {
    int dependencyCount = parentNode.getChildCount();
    for (int i = 0; i < dependencyCount; i++) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)parentNode.getChildAt(i);
      Object userObject = childNode.getUserObject();
      if (userObject instanceof AbstractPsdNode) {
        AbstractPsdNode node = (AbstractPsdNode)userObject;
        if (node.matches(dependencyModel)) {
          TreePath path = new TreePath(childNode.getPath());
          selectionPaths.add(path);
        }
      }
      collectMatching(dependencyModel, childNode, selectionPaths);
    }
  }

  private void updateSelection(@NotNull List<TreePath> selectionPaths) {
    if (!selectionPaths.isEmpty()) {
      // Remove TreeSelectionListener. We only want the selection event when the user selects a tree node directly. If we got here is
      // because the user selected a dependency in the "Dependencies" table, and we are simply syncing the tree.
      myTree.removeTreeSelectionListener(myTreeSelectionListener);

      myTree.getSelectionModel().clearSelection();
      myTree.setSelectionPaths(selectionPaths.toArray(new TreePath[selectionPaths.size()]));

      // Add TreeSelectionListener again, to react when user selects a tree node directly.
      myTree.addTreeSelectionListener(myTreeSelectionListener);
    }
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
