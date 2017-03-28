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
package com.android.tools.idea.monitor.ui.memory.model;

import com.android.tools.idea.editors.allocations.nodes.AbstractTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Model class that represents each row in the memory allocation table view. TODO we should merge
 * this with what's used in the current studio allocation view once we finalize on the device data
 * structure. e.g. {@link com.android.tools.idea.editors.allocations.nodes.AllocNode}
 */
public class MemoryInfoTreeNode extends DefaultMutableTreeNode {

  @Nullable protected MemoryInfoTreeNode myParent;

  @Nullable private Comparator<MemoryInfoTreeNode> myComparator = null;

  @NotNull private Vector<MemoryInfoTreeNode> myChildren = new Vector<>();

  private String mName;

  int mCount;

  public MemoryInfoTreeNode(String name) {
    mName = name;
  }

  public String getName() {
    return mName;
  }

  public void setCount(int count) {
    mCount = count;
  }

  public int getCount() {
    return mCount;
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
    assert treeNode instanceof MemoryInfoTreeNode;
    return myChildren.indexOf(treeNode);
  }

  @Override
  public boolean isLeaf() {
    return myChildren.size() == 0;
  }

  @Override
  public Enumeration children() {
    ensureOrder();
    return Collections.enumeration(myChildren);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    MemoryInfoTreeNode child = (MemoryInfoTreeNode) newChild;
    assert child != null;
    child.myParent = this;
    myChildren.add(childIndex, child);
  }

  @Override
  public void remove(int childIndex) {
    MemoryInfoTreeNode child = myChildren.get(childIndex);
    assert child != null;

    child.myParent = null;
    myChildren.remove(childIndex);
  }

  private void ensureOrder() {
    if ((myParent != null && myParent.myComparator != myComparator)
        || myParent == null && myComparator != null) {
      myComparator = myParent != null ? myParent.myComparator : myComparator;
      Collections.sort(myChildren, myComparator);
    }
  }

  public void sort(@NotNull Comparator<MemoryInfoTreeNode> comparator) {
    assert myParent == null;
    myComparator = comparator;
    ensureOrder();
  }
}
