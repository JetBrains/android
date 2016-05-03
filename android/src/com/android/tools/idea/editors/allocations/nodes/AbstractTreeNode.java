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
package com.android.tools.idea.editors.allocations.nodes;

import com.android.tools.adtui.ValuedTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Vector;

public abstract class AbstractTreeNode implements ValuedTreeNode {

  @Nullable protected AbstractTreeNode myParent;

  @Nullable private Comparator<AbstractTreeNode> myOrder = null;

  int myCount;

  int myValue;

  @NotNull private Vector<AbstractTreeNode> myChildren = new Vector<AbstractTreeNode>();

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
    assert treeNode instanceof AbstractTreeNode;
    return myChildren.indexOf(treeNode);
  }

  @Override
  public boolean isLeaf() {
    return myChildren.size() == 0;
  }

  @Override
  public Enumeration children() {
    ensureOrder();
    return myChildren.elements();
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public int getCount() {
    return myCount;
  }

  @Override
  public int getValue() {
    return myValue;
  }

  private void add(int count, int value) {
    myCount += count;
    myValue += value;
    if (myParent != null) {
      myParent.add(count, value);
    }
  }

  public void addChild(AbstractTreeNode node) {
    myChildren.add(node);
    node.myParent = this;
    add(node.getCount(), node.getValue());
  }

  private void ensureOrder() {
    if ((myParent != null && myParent.myOrder != myOrder) || myParent == null && myOrder != null) {
      myOrder = myParent != null ? myParent.myOrder : myOrder;
      Collections.sort(myChildren, myOrder);
    }
  }

  public void sort(@NotNull Comparator<AbstractTreeNode> order) {
    assert myParent == null;
    myOrder = order;
    ensureOrder();
  }
}
