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
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.DummyModel;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BottomUpNode extends CpuTreeNode<BottomUpNode> {

  private final List<CaptureNode> myPathNodes = new ArrayList<>();
  private final boolean myIsRoot;
  private boolean myChildrenBuilt;

  private BottomUpNode(String id) {
    super(id);
    myIsRoot = false;
    myChildrenBuilt = false;
  }

  public BottomUpNode(@NotNull CaptureNode node) {
    super("Root");
    myIsRoot = true;
    myChildrenBuilt = true;

    List<CaptureNode> allNodes = new ArrayList<>();
    // Pre-order traversal with Stack.
    // The traversal will sort nodes by CaptureNode#getStart(), if they'll be equal then ancestor will come first.
    Stack<CaptureNode> stack = new Stack<>();
    stack.add(node);
    while (!stack.isEmpty()) {
      CaptureNode curNode = stack.pop();
      allNodes.add(curNode);
      // Adding in reverse order so that the first child is processed first
      for (int i = curNode.getChildren().size() - 1; i >= 0; --i) {
        stack.add(curNode.getChildren().get(i));
      }
    }

    // We use a separate map for unmatched children, because we can not merge unmatched with matched,
    // i.e all merged children should have the same {@link CaptureNode.FilterType};
    Map<String, BottomUpNode> children = new HashMap<>();
    Map<String, BottomUpNode> unmatchedChildren = new HashMap<>();

    for (CaptureNode curNode : allNodes) {
      assert curNode.getData() != null;
      String curId = curNode.getData().getId();

      BottomUpNode child = curNode.isUnmatched() ? unmatchedChildren.get(curId) : children.get(curId);
      if (child == null) {
        child = new BottomUpNode(curId);
        if (curNode.isUnmatched()) {
          unmatchedChildren.put(curId, child);
        }
        else {
          children.put(curId, child);
        }
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

  private void addPathNode(@NotNull CaptureNode node) {
    myPathNodes.add(node);
  }

  public boolean buildChildren() {
    if (myChildrenBuilt) {
      return false;
    }

    // We use a separate map for unmatched children, because we can not merge unmatched with matched,
    // i.e all merged children should have the same {@link CaptureNode.FilterType};
    Map<String, BottomUpNode> children = new HashMap<>();
    Map<String, BottomUpNode> unmatchedChildren = new HashMap<>();

    assert myPathNodes.size() == getNodes().size();
    for (int i = 0; i < myPathNodes.size(); ++i) {
      CaptureNode parent = myPathNodes.get(i).getParent();
      if (parent == null) {
        continue;
      }
      assert parent.getData() != null;
      String parentId = parent.getData().getId();
      BottomUpNode child = parent.isUnmatched() ? unmatchedChildren.get(parentId) : children.get(parentId);
      if (child == null) {
        child = new BottomUpNode(parentId);
        if (parent.isUnmatched()) {
          unmatchedChildren.put(parentId, child);
        }
        else {
          children.put(parentId, child);
        }
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
    HNode<CaptureNodeModel> outerSoFar = null;

    // myNodes is sorted by CaptureNode#getStart() in increasing order,
    // if they are equal then ancestor comes first
    for (CaptureNode node : myNodes) {
      if (outerSoFar == null || node.getEnd() > outerSoFar.getEnd()) {
        if (outerSoFar != null) {
          // |outerSoFar| is at the top of the call stack
          myTotal += getIntersection(range, outerSoFar);
        }
        outerSoFar = node;
      }

      self += getIntersection(range, node);
      for (CaptureNode child : node.getChildren()) {
        self -= getIntersection(range, child);
      }
    }

    if (outerSoFar != null) {
      // |outerSoFar| is at the top of the call stack
      myTotal += getIntersection(range, outerSoFar);
    }
    myChildrenTotal = myTotal - self;
  }

  @NotNull
  @Override
  public CaptureNodeModel getMethodModel() {
    if (myIsRoot) {
      // Return a dummy entry for the root.
      return new DummyModel();
    }
    CaptureNodeModel model = myPathNodes.get(0).getData();
    assert model != null;
    return model;
  }

  @Override
  public CaptureNode.FilterType getFilterType() {
    if (myIsRoot) {
      return CaptureNode.FilterType.MATCH;
    }
    return myPathNodes.get(0).getFilterType();
  }
}
