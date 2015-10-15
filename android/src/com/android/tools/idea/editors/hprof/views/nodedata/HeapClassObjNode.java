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
package com.android.tools.idea.editors.hprof.views.nodedata;

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Instance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.Enumeration;
import java.util.List;

public class HeapClassObjNode implements HeapNode {
  @Nullable private HeapNode myParent;
  @NotNull private ClassObj myClassObj;
  private long myRetainedSize;
  private String mySimpleName;

  public HeapClassObjNode(@NotNull ClassObj classObj, int heapId) {
    myClassObj = classObj;
    for (Instance instance : myClassObj.getHeapInstances(heapId)) {
      myRetainedSize += instance.getTotalRetainedSize();
    }

    mySimpleName = myClassObj.getClassName();
    int index = mySimpleName.lastIndexOf('.');
    if (index >= 0 && index < mySimpleName.length() - 1) {
      mySimpleName = mySimpleName.substring(index + 1, mySimpleName.length());
    }
  }

  @NotNull
  public ClassObj getClassObj() {
    return myClassObj;
  }

  @NotNull
  @Override
  public String getFullName() {
    return getClassObj().getClassName();
  }

  @Override
  @NotNull
  public String getSimpleName() {
    return mySimpleName;
  }

  @Override
  public int getTotalCount() {
    return getClassObj().getInstanceCount();
  }

  @Override
  public int getHeapInstancesCount(int heapId) {
    return getClassObj().getHeapInstancesCount(heapId);
  }

  @Override
  public int getInstanceSize() {
    return getClassObj().getInstanceSize();
  }

  @Override
  public int getShallowSize(int heapId) {
    return getClassObj().getShallowSize(heapId);
  }

  @Override
  public long getRetainedSize() {
    return myRetainedSize;
  }

  @Override
  public void add(@NotNull HeapNode heapNode) {
    throw new RuntimeException("Invalid operation on " + getClass().getSimpleName());
  }

  @NotNull
  @Override
  public List<HeapNode> getChildren() {
    throw new RuntimeException("Invalid operation on " + getClass().getSimpleName());
  }

  @Override
  public void removeAllChildren() {
    throw new RuntimeException("Invalid operation on " + getClass().getSimpleName());
  }

  @Override
  public TreeNode getChildAt(int childIndex) {
    throw new RuntimeException("Invalid operation on " + getClass().getSimpleName());
  }

  @Override
  public int getChildCount() {
    return 0;
  }

  @Override
  public TreeNode getParent() {
    return myParent;
  }

  @Override
  public int getIndex(TreeNode node) {
    throw new RuntimeException("Invalid operation on " + getClass().getSimpleName());
  }

  @Override
  public boolean getAllowsChildren() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  @Override
  public Enumeration children() {
    throw new RuntimeException("Invalid operation on " + getClass().getSimpleName());
  }

  @Override
  public void removeFromParent() {
    if (myParent != null) {
      assert myParent instanceof HeapPackageNode;
      ((HeapPackageNode)myParent).remove(this);
      myParent = null;
    }
  }

  @Override
  public void setParent(@Nullable HeapNode newParent) {
    assert newParent == null || newParent instanceof HeapPackageNode;
    myParent = newParent;
  }
}
