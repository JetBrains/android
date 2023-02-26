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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * Model class that represents each row in the memory allocation table view.
 * TODO we should merge this with what's used in the current studio allocation view once we finalize on the device data structure.
 * e.g. {@link com.android.tools.idea.editors.allocations.nodes.AllocNode}
 */
public class MemoryObjectTreeNode<T extends MemoryObject> implements MutableTreeNode {
  @Nullable protected MemoryObjectTreeNode<T> myParent;

  @NotNull protected List<MemoryObjectTreeNode<T>> myChildren = new ArrayList<>();

  @Nullable protected Comparator<MemoryObjectTreeNode<T>> myComparator = null;

  @NotNull private final T myAdapter;

  private boolean myChildrenChanged;

  private boolean myComparatorChanged;

  public MemoryObjectTreeNode(@NotNull T adapter) {
    myAdapter = adapter;
  }

  @Override
  public TreeNode getChildAt(int i) {
    ensureOrder();
    return myChildren.get(i);
  }

  @Override
  public int getChildCount() {
    return myChildren.size();
  }

  @Override
  public TreeNode getParent() {
    return myParent;
  }

  @Override
  public int getIndex(TreeNode treeNode) {
    assert treeNode instanceof MemoryObjectTreeNode;
    ensureOrder();
    return myChildren.indexOf(treeNode);
  }

  @Override
  public boolean isLeaf() {
    return myChildren.isEmpty();
  }

  @Override
  public Enumeration children() {
    ensureOrder();
    return Collections.enumeration(myChildren);
  }

  public @NotNull List<MemoryObjectTreeNode<T>> getChildren() {
    ensureOrder();
    return List.copyOf(myChildren);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  public void add(@NotNull MemoryObjectTreeNode child) {
    insert(child, myChildren.size());
    myChildrenChanged = true;
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    assert newChild instanceof MemoryObjectTreeNode;
    MemoryObjectTreeNode child = (MemoryObjectTreeNode)newChild;
    if (child.myParent != null && child.myParent != this) {
      child.myParent.remove(child);
    }
    child.setParent(this);
    myChildren.add(childIndex, child);
    myChildrenChanged = true;
  }

  @Override
  public void setParent(@Nullable MutableTreeNode newParent) {
    assert newParent == null || newParent instanceof MemoryObjectTreeNode;
    myParent = (MemoryObjectTreeNode<T>)newParent;
  }

  @Override
  public void remove(int childIndex) {
    MemoryObjectTreeNode child = myChildren.get(childIndex);
    myChildren.remove(childIndex);
    child.setParent(null);
    myChildrenChanged = true;
  }

  @Override
  public void remove(MutableTreeNode node) {
    assert node instanceof MemoryObjectTreeNode;
    remove(myChildren.indexOf(node));
    myChildrenChanged = true;
  }

  @Override
  public void removeFromParent() {
    if (myParent != null) {
      myParent.remove(this);
      myParent = null;
    }
  }

  public void removeAll() {
    myChildren.forEach(child -> child.myParent = null);
    myChildren.clear();
    myChildrenChanged = true;
  }

  @Override
  public void setUserObject(Object object) {
    throw new RuntimeException("Not implemented, use setData/getAdapter instead.");
  }

  @NotNull
  public T getAdapter() {
    return myAdapter;
  }

  public void sort(@NotNull Comparator<MemoryObjectTreeNode<T>> comparator) {
    // Note - this can only be called on the root node.
    assert myParent == null;
    if (myComparator != comparator) {
      myComparator = comparator;
      myComparatorChanged = true;
      ensureOrder();
    }
  }

  @Nullable
  public Comparator<MemoryObjectTreeNode<T>> getComparator() {
    return myComparator;
  }

  @NotNull
  public List<MemoryObjectTreeNode<T>> getPathToRoot() {
    List<MemoryObjectTreeNode<T>> path = new ArrayList<>();
    MemoryObjectTreeNode<T> currentNode = this;
    MemoryObjectTreeNode<T> cycleDetector = this;
    while (currentNode != null) {
      for (int i = 0; i < 2 && cycleDetector != null; i++) {
        assert cycleDetector.myParent != currentNode;
        cycleDetector = cycleDetector.myParent;
      }

      path.add(currentNode);
      currentNode = currentNode.myParent;
    }
    Collections.reverse(path);
    return path;
  }

  protected void ensureOrder() {
    if (orderNeedsUpdating()) {
      myComparator = myParent != null ? myParent.myComparator : myComparator;
      if (myComparator != null) {
        myChildren.sort(myComparator);
      }

      myComparatorChanged = false;
      myChildrenChanged = false;
    }
  }

  @VisibleForTesting
  boolean orderNeedsUpdating() {
    return (myParent != null && myParent.myComparator != myComparator) || myComparatorChanged || myChildrenChanged;
  }

  /**
   * Optimization - a callback mechanism to notify the node it has been selected in the tree. This allows us to modify the node's content
   * on the fly, such as adding additional nodes which helps to avoid populating too many nodes at once.
   */
  public void select() {
  }
}