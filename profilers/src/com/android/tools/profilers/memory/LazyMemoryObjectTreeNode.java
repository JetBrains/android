/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.Enumeration;
import java.util.List;

/**
 * This class optimizes tree nodes building/expansion to prevent running out of memory on tree construction when there are many many nodes:
 *
 * 1. A node's children are not constructed until {@link #expandNode()} is called.
 * 2. A node's children are paged in on demand once the count surpasses {@link #NUM_CHILDREN_PER_PAGE}, a placeholder paging node is added
 * to the end which, when selected, would dynamically page in more sibling nodes.
 */
public abstract class LazyMemoryObjectTreeNode<T extends MemoryObject> extends MemoryObjectTreeNode<T> {
  static final int INVALID_CHILDREN_COUNT = -1;
  static final int NUM_CHILDREN_PER_PAGE = 100;

  protected int myMemoizedChildrenCount = INVALID_CHILDREN_COUNT;

  private int myCurrentPageCount;
  /**
   * A partial view into the full list of children. It always starts at 0 but will be bounded by:
   * Math.min(myChildren.size(), myCurrentPageCount * NUM_CHILDREN_PER_PAGE);
   */
  private List<MemoryObjectTreeNode<T>> myChildrenView;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private MemoryObjectTreeNode<MemoryObject> myPagingNode;

  public LazyMemoryObjectTreeNode(@NotNull T adapter, boolean usePaging) {
    super(adapter);

    myCurrentPageCount = 1;
    myChildrenView = myChildren.subList(0, 0);
    myPagingNode = usePaging ? new PagingNode(this) : null;
  }

  /**
   * A hackish solution to allow us to fire a nodeStructuredChanged event on the TreeModel when more nodes are paged in.
   */
  public void setTreeModel(@Nullable DefaultTreeModel treeModel) {
    myTreeModel = treeModel;
  }

  @Nullable
  public DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  public abstract int computeChildrenCount();

  public abstract void expandNode();

  @Override
  public TreeNode getChildAt(int i) {
    expandNode();
    ensureOrder();

    if (myPagingNode != null && myChildren.size() == myChildrenView.size() && i >= myChildren.size()) {
      // Custom exception handling for the case where all children are displayed.
      // Otherwise we allow the index to be myChildrenView.size() + 1 to account for the paging node.
      throw new IndexOutOfBoundsException();
    }
    return myPagingNode != null && i == myChildrenView.size() ? myPagingNode : myChildren.get(i);
  }

  @Override
  public int getChildCount() {
    if (myMemoizedChildrenCount == INVALID_CHILDREN_COUNT) {
      myMemoizedChildrenCount = computeChildrenCount();
      if (myPagingNode == null) {
        myCurrentPageCount = (myMemoizedChildrenCount + NUM_CHILDREN_PER_PAGE - 1) / NUM_CHILDREN_PER_PAGE;
        myChildrenView = myChildren;
      }
    }

    return myPagingNode != null && myMemoizedChildrenCount > myCurrentPageCount * NUM_CHILDREN_PER_PAGE
           ? myCurrentPageCount * NUM_CHILDREN_PER_PAGE + 1
           : myMemoizedChildrenCount;
  }

  @Override
  public int getIndex(TreeNode treeNode) {
    expandNode();
    ensureOrder();

    if (treeNode == null) {
      return -1;
    }
    if (treeNode instanceof LazyMemoryObjectTreeNode) {
      return myPagingNode == null ? myChildren.indexOf(treeNode) : myChildrenView.indexOf(treeNode);
    }
    else if (treeNode == myPagingNode) {
      return myChildrenView.size();
    }
    return -1;
  }

  @Override
  public boolean isLeaf() {
    return getChildCount() == 0;
  }

  @Override
  public Enumeration children() {
    expandNode();
    return super.children();
  }

  @Override
  public @NotNull List<MemoryObjectTreeNode<T>> getChildren() {
    expandNode();
    return super.getChildren();
  }

  @VisibleForTesting
  ImmutableList<MemoryObjectTreeNode<T>> getBuiltChildren() {
    ensureOrder();
    return ContainerUtil.immutableList(myChildrenView);
  }

  public void reset() {
    removeAll();
    myMemoizedChildrenCount = INVALID_CHILDREN_COUNT;
    myCurrentPageCount = 1;
    myChildrenView = myChildren.subList(0, 0);
  }

  @Override
  protected void ensureOrder() {
    getChildCount();
    super.ensureOrder();
    myChildrenView = myChildren.subList(
      0, Math.min(myChildren.size(), myPagingNode != null ? myCurrentPageCount * NUM_CHILDREN_PER_PAGE : myMemoizedChildrenCount));
  }

  private static class PagingNode extends MemoryObjectTreeNode<MemoryObject> {
    @NotNull private final LazyMemoryObjectTreeNode myOwnerNode;

    public PagingNode(@NotNull LazyMemoryObjectTreeNode ownerNode) {
      super(() -> String.format("Click to see next %d...",
                                Math.min(NUM_CHILDREN_PER_PAGE, ownerNode.myChildren.size() - ownerNode.myChildrenView.size())));
      myOwnerNode = ownerNode;
    }

    @Override
    public void select() {
      myOwnerNode.myCurrentPageCount++;
      int[] newIndices;
      if (myOwnerNode.myCurrentPageCount * NUM_CHILDREN_PER_PAGE >= myOwnerNode.myChildren.size()) {
        newIndices = new int[myOwnerNode.myChildren.size() - myOwnerNode.myChildrenView.size()];
      }
      else {
        // Note the +1 offset to account for the paging node that needs to be appended to the end.
        newIndices = new int[NUM_CHILDREN_PER_PAGE + 1];
      }

      for (int i = 0; i < newIndices.length; i++) {
        newIndices[i] = myOwnerNode.myChildrenView.size() + i;
      }

      assert myOwnerNode.myTreeModel != null;
      int previousViewSize = myOwnerNode.myChildrenView.size();
      myOwnerNode.myChildrenView =
        myOwnerNode.myChildren
          .subList(0, Math.min(myOwnerNode.myChildren.size(), myOwnerNode.myCurrentPageCount * NUM_CHILDREN_PER_PAGE));
      // First remove the existing paging node.
      myOwnerNode.myTreeModel.nodesWereRemoved(myOwnerNode, new int[]{previousViewSize}, new Object[]{this});
      // Fires the rest of the new node insertion events.
      myOwnerNode.myTreeModel.nodesWereInserted(myOwnerNode, newIndices);
    }
  }
}
