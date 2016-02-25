/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.prototype.editor.dependencies;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.structure.prototype.editor.dependencies.DependenciesTreeStructure.ArtifactNode;
import com.android.tools.idea.gradle.structure.prototype.editor.dependencies.DependenciesTreeStructure.DependencyNode;
import com.android.tools.idea.gradle.structure.prototype.editor.dependencies.DependenciesTreeStructure.ModuleNode;
import com.android.tools.idea.gradle.structure.prototype.model.ArtifactDependencyMergedModel;
import com.android.tools.idea.gradle.structure.prototype.model.DependencyMergedModel;
import com.android.tools.idea.gradle.structure.prototype.model.ModuleDependencyMergedModel;
import com.android.tools.idea.structure.dialog.Header;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.structure.prototype.model.Coordinates.areEqual;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

class DependenciesTreePanel extends JPanel {
  @NotNull private final Tree myTree;
  @NotNull private final DependenciesTreeBuilder myTreeBuilder;

  private volatile boolean myProgrammaticSelection;

  DependenciesTreePanel(@NotNull final DependenciesPanel dependenciesPanel) {
    super(new BorderLayout());
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree = new Tree(treeModel);
    myTree.setExpandsSelectedPaths(true);
    myTree.setRootVisible(false);
    myTree.getSelectionModel().setSelectionMode(DISCONTIGUOUS_TREE_SELECTION);

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myProgrammaticSelection) {
          myProgrammaticSelection = false;
          return;
        }
        Set<DependencyNode> selection = myTreeBuilder.getSelectedElements(DependencyNode.class);
        if (selection.size() == 1) {
          DependencyNode selected = getFirstItem(selection);
          assert selected != null;

          DependencyMergedModel dependency = selected.dependencyModel;
          if (dependency == null) {
            if (selected instanceof ArtifactNode) {
              selectArtifact(((ArtifactNode)selected).coordinate);
            }
          }
          else {
            boolean dependencySelected = dependenciesPanel.select(dependency);
            if (!dependencySelected) {
              select(dependency);
            }
          }
        }
      }
    });

    myTreeBuilder = new DependenciesTreeBuilder(dependenciesPanel, myTree, treeModel);

    add(new Header("Variants"), BorderLayout.NORTH);
    add(new JBScrollPane(myTree), BorderLayout.CENTER);

    ActionCallback initialized = myTreeBuilder.getInitialized();
    initialized.doWhenDone(new Runnable() {
      @Override
      public void run() {
        dependenciesPanel.selectInTreeView();
      }
    });
  }

  void clearSelection() {
    myTree.getSelectionModel().clearSelection();
  }

  void select(@NotNull DependencyMergedModel dependency) {
    if (dependency instanceof ArtifactDependencyMergedModel) {
      ArtifactDependencyMergedModel artifactDependency = (ArtifactDependencyMergedModel)dependency;
      selectArtifact(artifactDependency.getCoordinate());
    }
    else if (dependency instanceof ModuleDependencyMergedModel) {
      ModuleDependencyMergedModel moduleDependency = (ModuleDependencyMergedModel)dependency;
      selectModule(moduleDependency.getGradlePath());
    }
  }

  private void selectArtifact(@NotNull GradleCoordinate coordinate) {
    DefaultMutableTreeNode rootNode = myTreeBuilder.getRootNode();
    if (rootNode != null) {
      List<TreePath> selectionPaths = Lists.newArrayList();

      int variantCount = rootNode.getChildCount();
      for (int i = 0; i < variantCount; i++) {
        DefaultMutableTreeNode variantNode = (DefaultMutableTreeNode)rootNode.getChildAt(i);
        collectMatchingDependencies(coordinate, variantNode, selectionPaths);
      }
      updateProgrammaticSelection(selectionPaths);
    }
  }

  private void selectModule(@NotNull String gradlePath) {
    DefaultMutableTreeNode rootNode = myTreeBuilder.getRootNode();
    if (rootNode != null) {
      List<TreePath> selectionPaths = Lists.newArrayList();

      int variantCount = rootNode.getChildCount();
      for (int i = 0; i < variantCount; i++) {
        DefaultMutableTreeNode variantNode = (DefaultMutableTreeNode)rootNode.getChildAt(i);
        collectMatchingDependencies(gradlePath, variantNode, selectionPaths);
      }
      updateProgrammaticSelection(selectionPaths);
    }
  }

  private void updateProgrammaticSelection(@NotNull List<TreePath> selectionPaths) {
    if (!selectionPaths.isEmpty()) {
      clearSelection();
      myProgrammaticSelection = true;
      myTree.setSelectionPaths(selectionPaths.toArray(new TreePath[selectionPaths.size()]));
    }
  }

  private static void collectMatchingDependencies(@NotNull GradleCoordinate coordinate,
                                                  @NotNull DefaultMutableTreeNode parentNode,
                                                  @NotNull List<TreePath> selectionPaths) {
    int dependencyCount = parentNode.getChildCount();

    for (int i = 0; i < dependencyCount; i++) {
      DefaultMutableTreeNode dependencyNode = (DefaultMutableTreeNode)parentNode.getChildAt(i);
      Object userObject = dependencyNode.getUserObject();
      if (userObject instanceof ArtifactNode) {
        ArtifactNode artifactNode = (ArtifactNode)userObject;
        if (areEqual(artifactNode.coordinate, coordinate)) {
          TreePath path = new TreePath(dependencyNode.getPath());
          selectionPaths.add(path);
        }
        collectMatchingDependencies(coordinate, dependencyNode, selectionPaths);
      }
    }
  }

  private static void collectMatchingDependencies(@NotNull String gradlePath,
                                                  @NotNull DefaultMutableTreeNode parentNode,
                                                  @NotNull List<TreePath> selectionPaths) {
    int dependencyCount = parentNode.getChildCount();

    for (int i = 0; i < dependencyCount; i++) {
      DefaultMutableTreeNode dependencyNode = (DefaultMutableTreeNode)parentNode.getChildAt(i);
      Object userObject = dependencyNode.getUserObject();
      if (userObject instanceof ModuleNode) {
        ModuleNode moduleNode = (ModuleNode)userObject;
        if (gradlePath.equals(moduleNode.getGradlePath())) {
          TreePath path = new TreePath(dependencyNode.getPath());
          selectionPaths.add(path);
        }
      }
    }
  }
}
