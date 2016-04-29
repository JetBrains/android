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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.tools.idea.gradle.structure.configurables.editor.treeview.GradleNode;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;

class DependenciesTreeBuilder extends AbstractTreeBuilder {
  DependenciesTreeBuilder(@NotNull DependenciesPanel dependenciesPanel, @NotNull Tree tree, @NotNull DefaultTreeModel treeModel) {
    super(tree, treeModel, new DependenciesTreeStructure(dependenciesPanel), IndexComparator.INSTANCE);
    initRootNode();
    dependenciesPanel.registerDisposable(this);
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    if (nodeDescriptor instanceof GradleNode) {
      GradleNode node = (GradleNode)nodeDescriptor;
      return node.isAutoExpand();
    }
    return super.isAutoExpandNode(nodeDescriptor);
  }
}
