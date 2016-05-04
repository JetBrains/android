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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractPsNodeTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.LibraryDependencyNode;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidLibraryDependency;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class DeclaredDependenciesTreeBuilder extends AbstractPsNodeTreeBuilder {
  public DeclaredDependenciesTreeBuilder(@NotNull PsProject project,
                                         @NotNull JTree tree,
                                         @NotNull DefaultTreeModel treeModel) {
    super(tree, treeModel, new DeclaredDependenciesTreeStructure(project));
  }

  public void reset(@Nullable Runnable onDone) {
    AbstractTreeStructure treeStructure = getTreeStructure();
    if (treeStructure instanceof DeclaredDependenciesTreeStructure) {
      ((DeclaredDependenciesTreeStructure)treeStructure).reset();
      ActionCallback callback = queueUpdate();
      if (onDone != null) {
        callback.doWhenDone(onDone);
      }
    }
  }

  @Nullable
  public LibraryDependencyNode find(@NotNull PsArtifactDependencySpec spec) {
    DefaultMutableTreeNode rootNode = getRootNode();
    if (rootNode != null) {
      int childCount = rootNode.getChildCount();
      for (int i = 0; i < childCount; i++) {
        TreeNode child = rootNode.getChildAt(i);
        if (child instanceof DefaultMutableTreeNode) {
          Object userObject = ((DefaultMutableTreeNode)child).getUserObject();
          if (userObject instanceof LibraryDependencyNode) {
            LibraryDependencyNode node = (LibraryDependencyNode)userObject;
            for (PsAndroidLibraryDependency dependency : node.getModels()) {
              if (spec.equals(dependency.getDeclaredSpec())) {
                return node;
              }
            }
          }
        }
      }
    }
    return null;
  }
}
