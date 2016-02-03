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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.VariantsTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.android.tools.idea.structure.dialog.HeaderPanel;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.treeStructure.Tree;
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
import java.util.List;
import java.util.Set;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

class VariantTreeViewPanel extends JPanel implements Disposable {
  @NotNull private final Tree myTree;
  @NotNull private final VariantsTreeBuilder myTreeBuilder;
  @NotNull private final TreeSelectionListener myTreeSelectionListener;

  @NotNull private final List<SelectionListener> mySelectionListeners = Lists.newCopyOnWriteArrayList();

  VariantTreeViewPanel(@NotNull PsdAndroidModuleModel moduleModel) {
    super(new BorderLayout());
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree = new Tree(treeModel);
    myTree.setExpandsSelectedPaths(true);
    myTree.setRootVisible(false);

    TreeSelectionModel selectionModel = myTree.getSelectionModel();
    selectionModel.setSelectionMode(DISCONTIGUOUS_TREE_SELECTION);

    myTreeBuilder = new VariantsTreeBuilder(moduleModel, myTree, treeModel);

    add(new HeaderPanel("Variants"), BorderLayout.NORTH);

    JScrollPane scrollPane = createScrollPane(myTree);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    add(scrollPane, BorderLayout.CENTER);

    myTreeSelectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        PsdAndroidDependencyModel selected = getSingleSelection();
        if (selected != null) {
          select(selected);
          for (SelectionListener listener : mySelectionListeners) {
            listener.dependencyModelSelected(selected);
          }
        }
      }
    };
    myTree.addTreeSelectionListener(myTreeSelectionListener);
  }

  void select(@NotNull final PsdAndroidDependencyModel dependencyModel) {
    myTreeBuilder.getInitialized().doWhenDone(new Runnable() {
      @Override
      public void run() {
        DefaultMutableTreeNode rootNode = myTreeBuilder.getRootNode();
        if (rootNode != null) {
          List<TreePath> selectionPaths = Lists.newArrayList();

          int variantCount = rootNode.getChildCount();
          for (int i = 0; i < variantCount; i++) {
            DefaultMutableTreeNode variantNode = (DefaultMutableTreeNode)rootNode.getChildAt(i);
            collectMatching(dependencyModel, variantNode, selectionPaths);
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
      DefaultMutableTreeNode dependencyNode = (DefaultMutableTreeNode)parentNode.getChildAt(i);
      Object userObject = dependencyNode.getUserObject();
      if (userObject instanceof AbstractPsdNode) {
        AbstractPsdNode node = (AbstractPsdNode)userObject;
        if (node.matches(dependencyModel)) {
          TreePath path = new TreePath(dependencyNode.getPath());
          selectionPaths.add(path);
        }
      }
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
    PsdAndroidDependencyModel selected = getSingleSelection();
    if (selected != null) {
      listener.dependencyModelSelected(selected);
    }
    mySelectionListeners.add(listener);
  }

  @Nullable
  private PsdAndroidDependencyModel getSingleSelection() {
    Set<AbstractDependencyNode> selection = myTreeBuilder.getSelectedElements(AbstractDependencyNode.class);
    if (selection.size() == 1) {
      AbstractDependencyNode node = getFirstItem(selection);
      if (node != null) {
        return (PsdAndroidDependencyModel)node.getModel();
      }
    }
    return null;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myTreeBuilder);
    mySelectionListeners.clear();
  }

  public interface SelectionListener {
    void dependencyModelSelected(@NotNull PsdAndroidDependencyModel model);
  }
}
