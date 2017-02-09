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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BottomUpNode extends CpuTreeNode<BottomUpNode> {

  private final List<HNode<MethodModel>> myPathNodes = new ArrayList<>();
  private final boolean myIsRoot;
  private boolean myChildrenBuilt;

  public BottomUpNode(String id) {
    super(id);
    myIsRoot = false;
    myChildrenBuilt = false;
  }

  public BottomUpNode(@NotNull HNode<MethodModel> node) {
    super("Root");
    myIsRoot = true;
    myChildrenBuilt = true;

    List<HNode<MethodModel>> allNodes = new ArrayList<>();
    allNodes.add(node);
    int head = 0;
    while (head < allNodes.size()) {
      HNode<MethodModel> curNode = allNodes.get(head++);
      allNodes.addAll(curNode.getChildren());
    }

    allNodes.sort((o1, o2) -> {
      int cmp = Long.compare(o1.getStart(), o2.getStart());
      if (cmp != 0) {
        return cmp;
      }
      return -Long.compare(o1.getEnd(), o2.getEnd());
    });

    Map<String, BottomUpNode> children = new HashMap<>();
    for (HNode<MethodModel> curNode : allNodes) {
      assert curNode.getData() != null;
      String curId = curNode.getData().getId();
      BottomUpNode child = children.get(curId);
      if (child == null) {
        child = new BottomUpNode(curId);
        children.put(curId, child);
        addChild(child);
      }
      child.addPathNode(curNode);
      child.addNode(curNode);
    }

    addNode(node);

    for (BottomUpNode child : getChildren()) {
      child.buildChildren();
    }
  }

  private void addPathNode(@NotNull HNode<MethodModel> node) {
    myPathNodes.add(node);
  }

  public boolean buildChildren() {
    if (myChildrenBuilt) {
      return false;
    }

    Map<String, BottomUpNode> children = new HashMap<>();
    assert myPathNodes.size() == getNodes().size();
    for (int i = 0; i < myPathNodes.size(); ++i) {
      HNode<MethodModel> parent = myPathNodes.get(i).getParent();
      if (parent == null) {
        continue;
      }
      assert parent.getData() != null;
      String parentId = parent.getData().getId();
      BottomUpNode child = children.get(parentId);
      if (child == null) {
        child = new BottomUpNode(parentId);
        children.put(parentId, child);
        addChild(child);
      }
      child.addPathNode(parent);
      child.addNode(getNodes().get(i));
    }

    myChildrenBuilt = true;
    return true;
  }

  @Override
  public void update(@NotNull Range range) {
    // how much time was spent in this call stack path, and in the functions it called
    myTotal = 0;
    // how much time was spent doing work directly in this call stack path
    double self = 0;

    // The node that is at the top of the call stack, e.g if the call stack looks like B [0..30] -> B [1..20],
    // then the second method can't be outerSoFar.
    // It's used to exclude nodes which aren't at the top of the
    // call stack from the total time calculation.
    HNode<MethodModel> outerSoFar = null;

    // myNodes is sorted by HNode#getStart() in increasing order,
    // if they are equal then by HNode#getEnd() in decreasing order
    for (HNode<MethodModel> node: myNodes) {
      if (outerSoFar == null || node.getEnd() > outerSoFar.getEnd()) {
        if (outerSoFar != null) {
          // |outerSoFar| is at the top of the call stack
          myTotal += getIntersection(range, outerSoFar);
        }
        outerSoFar = node;
      }

      self += getIntersection(range, node);
      for (HNode<MethodModel> child : node.getChildren()) {
        self -= getIntersection(range, child);
      }
    }

    if (outerSoFar != null) {
      // |outerSoFar| is at the top of the call stack
      myTotal += getIntersection(range, outerSoFar);
    }
    myChildrenTotal = myTotal - self;
  }

  @Override
  public String getMethodName() {
    if (myIsRoot) {
      return "";
    }
    MethodModel method = myPathNodes.get(0).getData();
    return (method == null ? "" : method.getName());
  }

  @Override
  public String getClassName() {
    if (myIsRoot) {
      return "";
    }
    MethodModel method = myPathNodes.get(0).getData();
    return (method == null ? "" : method.getClassName());
  }
}
