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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class CpuTraceTreeSorter implements ColumnTreeBuilder.TreeSorter<DefaultMutableTreeNode> {

  @NotNull private JTree myTree;
  private DefaultMutableTreeNode myRoot;
  private CpuTreeModel myModel;
  private Comparator<DefaultMutableTreeNode> myComparator;

  public CpuTraceTreeSorter(@NotNull JTree tree) {
    myTree = tree;
  }

  public void setModel(CpuTreeModel model, Comparator<DefaultMutableTreeNode> sorting) {
    myModel = model;
    if (myModel != null) {
      myRoot = (DefaultMutableTreeNode)model.getRoot();
      sort(sorting, SortOrder.UNSORTED); //SortOrder Parameter is not used.
      myTree.invalidate();
    }
  }

  @Override
  public void sort(Comparator<DefaultMutableTreeNode> comparator, SortOrder order) {
    myComparator = new MatchedNodeFirstComparator(comparator);
    sort();
  }

  private void sortTree(@NotNull DefaultMutableTreeNode parent) {
    if (parent.isLeaf()) {
      return;
    }
    int childCount = parent.getChildCount();
    List< DefaultMutableTreeNode> children = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      children.add((DefaultMutableTreeNode) parent.getChildAt(i));
    }

    Collections.sort(children, myComparator);
    parent.removeAllChildren();
    for (DefaultMutableTreeNode node: children) {
      sortTree(node);
      parent.add(node);
    }
  }

  public void sort() {
    if (myModel != null && myRoot != null) {
      TreePath selectionPath = myTree.getSelectionPath();
      sortTree(myRoot);
      myTree.collapseRow(0);
      myTree.setSelectionPath(selectionPath);
      myTree.scrollPathToVisible(selectionPath);
      myModel.reload();
    }
  }

  /**
   * Comparator where nodes with filter type {@link CaptureNode.FilterType#UNMATCH} comes after than others.
   */
  private static class MatchedNodeFirstComparator implements Comparator<DefaultMutableTreeNode> {
    @NotNull private Comparator<DefaultMutableTreeNode> myComparator;

    MatchedNodeFirstComparator(@NotNull Comparator<DefaultMutableTreeNode> comparator) {
      myComparator = comparator;
    }

    @Override
    public int compare(DefaultMutableTreeNode a, DefaultMutableTreeNode b) {
      CpuTreeNode o1 = ((CpuTreeNode)a.getUserObject());
      CpuTreeNode o2 = ((CpuTreeNode)b.getUserObject());
      int cmp = Boolean.compare(o1.isUnmatched(), o2.isUnmatched());
      return (cmp == 0) ? myComparator.compare(a, b) : cmp;
    }
  }
}
