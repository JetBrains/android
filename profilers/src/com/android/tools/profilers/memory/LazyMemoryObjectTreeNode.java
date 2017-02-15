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
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import java.util.Enumeration;

/**
 * This class allows for lazy building and expansion of tree nodes. This way, if a parent node has a lot of child nodes, each with many many
 * of its own children data, this will prevent the tree from running out of memory on construction.
 */
public abstract class LazyMemoryObjectTreeNode<T extends MemoryObject> extends MemoryObjectTreeNode<T> {
  static final int INVALID_CHILDREN_COUNT = -1;

  protected int myMemoizedChildrenCount = INVALID_CHILDREN_COUNT;

  public LazyMemoryObjectTreeNode(@NotNull T adapter) {
    super(adapter);
  }

  public abstract int computeChildrenCount();

  public abstract void expandNode();

  @Override
  public TreeNode getChildAt(int i) {
    expandNode();
    return super.getChildAt(i);
  }

  @Override
  public int getChildCount() {
    if (myMemoizedChildrenCount == INVALID_CHILDREN_COUNT) {
      myMemoizedChildrenCount = computeChildrenCount();
    }
    return myMemoizedChildrenCount;
  }

  @Override
  public int getIndex(TreeNode treeNode) {
    expandNode();
    return super.getIndex(treeNode);
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

  @NotNull
  @Override
  public ImmutableList<MemoryObjectTreeNode<T>> getChildren() {
    expandNode();
    return super.getChildren();
  }

  @VisibleForTesting
  ImmutableList<MemoryObjectTreeNode<T>> getBuiltChildren() {
    return super.getChildren();
  }
}
