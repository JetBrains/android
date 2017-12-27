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

import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.*;

public class HeapPackageNode implements HeapNode {
  @Nullable private HeapNode myParent;
  @NotNull private List<HeapNode> myChildren = new ArrayList<HeapNode>();

  @NotNull private HashMap<String, HeapPackageNode> mySubPackages = new HashMap<String, HeapPackageNode>();
  @NotNull private Set<HeapClassObjNode> myClasses = new HashSet<HeapClassObjNode>();

  @SuppressWarnings("NullableProblems") @NotNull private String myFullPackageName;
  @NotNull private String myPackageName;

  private int myTotalCount;
  private int myHeapInstanceCount;
  private int myShallowSize;
  private long myRetainedSize;

  public HeapPackageNode(@Nullable HeapPackageNode parent, @NotNull String packageName) {
    myPackageName = packageName;
    setParent(parent);
  }

  public void update(int currentHeapId) {
    myRetainedSize = 0;
    for (HeapPackageNode heapPackageNode : mySubPackages.values()) {
      heapPackageNode.update(currentHeapId);
      updateCounts(heapPackageNode, currentHeapId);
    }

    for (HeapClassObjNode heapClassObjNode : myClasses) {
      updateCounts(heapClassObjNode, currentHeapId);
    }
  }

  private void updateCounts(@NotNull HeapNode heapNode, int currentHeapId) {
    myTotalCount += heapNode.getTotalCount();
    myHeapInstanceCount += heapNode.getHeapInstancesCount(currentHeapId);
    myShallowSize += heapNode.getShallowSize(currentHeapId);
    myRetainedSize += heapNode.getRetainedSize();
  }

  public void classifyClassObj(@NotNull HeapClassObjNode heapClassObjNode) {
    String className = heapClassObjNode.getClassObj().getClassName();
    assert className.startsWith(myFullPackageName) && className.length() > myFullPackageName.length() + 1; // Mind the dot.
    String remainder = myFullPackageName.isEmpty() ? className : className.substring(myFullPackageName.length() + 1);

    int dotIndex = remainder.indexOf('.');
    if (dotIndex > 0) {
      assert !remainder.isEmpty();
      String subPackageName = remainder.substring(0, dotIndex);
      HeapPackageNode heapPackageNode = mySubPackages.get(subPackageName);
      if (heapPackageNode == null) {
        heapPackageNode = new HeapPackageNode(this, subPackageName);
        mySubPackages.put(subPackageName, heapPackageNode);
      }
      heapPackageNode.classifyClassObj(heapClassObjNode);
    }
    else {
      myClasses.add(heapClassObjNode);
    }
  }

  public void clear() {
    removeAllChildren();
    mySubPackages.clear();
    myClasses.clear();
    myTotalCount = 0;
    myHeapInstanceCount = 0;
    myShallowSize = 0;
    myRetainedSize = 0;
  }

  @NotNull
  @Override
  public String getFullName() {
    return myFullPackageName;
  }

  @NotNull
  @Override
  public String getSimpleName() {
    return myPackageName;
  }

  @Override
  public int getTotalCount() {
    return myTotalCount;
  }

  @Override
  public int getHeapInstancesCount(int heapId) {
    return myHeapInstanceCount;
  }

  @Override
  public int getInstanceSize() {
    return -1;
  }

  @Override
  public int getShallowSize(int heapId) {
    return myShallowSize;
  }

  @Override
  public long getRetainedSize() {
    return myRetainedSize;
  }

  @Override
  public void add(@NotNull HeapNode heapNode) {
    heapNode.removeFromParent();
    heapNode.setParent(this);
    myChildren.add(heapNode);
  }

  @NotNull
  @Override
  public List<HeapNode> getChildren() {
    return myChildren;
  }

  @Override
  public void removeAllChildren() {
    for (HeapNode child : myChildren) {
      child.setParent(null);
    }
    myChildren.clear();
  }

  @NotNull
  public HashMap<String, HeapPackageNode> getSubPackages() {
    return mySubPackages;
  }

  public void buildTree() {
    for (HeapPackageNode heapPackageNode : mySubPackages.values()) {
      add(heapPackageNode);
      heapPackageNode.buildTree();
    }

    for (HeapClassObjNode heapClassObjNode : myClasses) {
      // TODO: Handle inner/anonymous classes.
      add(heapClassObjNode);
    }
  }

  @Override
  public TreeNode getChildAt(int childIndex) {
    return myChildren.get(childIndex);
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
  public int getIndex(TreeNode node) {
    assert node instanceof HeapNode;
    return myChildren.indexOf(node);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public boolean isLeaf() {
    return myChildren.isEmpty();
  }

  @Override
  public Enumeration children() {
    return Collections.enumeration(myChildren);
  }

  public void remove(@NotNull HeapNode node) {
    myChildren.remove(node);
  }

  @Override
  public void removeFromParent() {
    if (myParent != null) {
      assert myParent instanceof HeapPackageNode;
      ((HeapPackageNode)myParent).remove(this);
      setParent(null);
    }
  }

  @Override
  public void setParent(@Nullable HeapNode newParent) {
    assert newParent == null || newParent instanceof HeapPackageNode;
    myParent = newParent;
    myFullPackageName = myParent == null || myParent.getFullName().isEmpty() ? myPackageName : myParent.getFullName() + "." + myPackageName;
  }
}
