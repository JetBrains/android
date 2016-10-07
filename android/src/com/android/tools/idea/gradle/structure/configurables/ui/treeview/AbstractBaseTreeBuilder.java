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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview;

import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.util.Arrays;
import java.util.List;

import static com.intellij.util.ui.tree.TreeUtil.collapseAll;
import static com.intellij.util.ui.tree.TreeUtil.showRowCentered;

public abstract class AbstractBaseTreeBuilder extends AbstractTreeBuilder {
  private static final TreePath[] EMPTY_TREE_PATH = new TreePath[0];

  public AbstractBaseTreeBuilder(@NotNull JTree tree,
                                 @NotNull DefaultTreeModel treeModel,
                                 @NotNull AbstractBaseTreeStructure treeStructure) {
    super(tree, treeModel, treeStructure, IndexComparator.INSTANCE);
  }

  @Override
  public boolean isToEnsureSelectionOnFocusGained() {
    return false;
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    if (nodeDescriptor instanceof AbstractPsNode) {
      return ((AbstractPsNode)nodeDescriptor).isAutoExpandNode();
    }
    return super.isAutoExpandNode(nodeDescriptor);
  }

  @Override
  protected boolean isSmartExpand() {
    return true;
  }

  public void expandAllNodes() {
    JTree tree = getTree();
    if (tree != null) {
      clearSelection();
      TreeUtil.expandAll(tree);
      onAllNodesExpanded();
    }
  }

  protected void onAllNodesExpanded() {
  }

  public void collapseAllNodes() {
    JTree tree = getTree();
    if (tree != null) {
      collapseAll(tree, 1);
      clearSelection(tree);
    }
  }

  public void clearSelection() {
    JTree tree = getTree();
    if (tree != null) {
      clearSelection(tree);
    }
  }

  public void expandParents(@NotNull List<? extends SimpleNode> nodes) {
    List<SimpleNode> toExpand = Lists.newArrayList();
    for (SimpleNode node : nodes) {
      SimpleNode parent = node.getParent();
      if (parent != null) {
        toExpand.add(parent);
      }
    }
    expand(toExpand.toArray(), null);
  }

  public void scrollToFirstSelectedRow() {
    JTree tree = getTree();
    if (tree != null) {
      // Scroll to the first selected row in the tree.
      int[] selectionRows = tree.getSelectionRows();
      if (selectionRows != null && selectionRows.length > 0) {
        if (selectionRows.length > 1) {
          Arrays.sort(selectionRows);
        }
        int firstRow = selectionRows[0];
        showRowCentered(tree, firstRow, false, true);
      }
    }
  }

  private static void clearSelection(@NotNull JTree tree) {
    tree.setSelectionPaths(EMPTY_TREE_PATH);
  }
}
