/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.jetbrains.annotations.NotNull;

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

    // We use a separate map for unmatched children, because we can not merge unmatched with matched,
    // i.e all merged children should have the same {@link CaptureNode.FilterType};
    Map<String, BottomUpNode> children = new HashMap<>();
    Map<String, BottomUpNode> unmatchedChildren = new HashMap<>();

    // Pre-order traversal with Stack.
    // The traversal will sort nodes by CaptureNode#getStart(), if they'll be equal then ancestor will come first.
    Stack<CaptureNode> stack = new Stack<>();
    stack.add(node);
    while (!stack.isEmpty()) {
      CaptureNode curNode = stack.pop();
      // Adding in reverse order so that the first child is processed first
      for (int i = curNode.getChildren().size() - 1; i >= 0; --i) {
        stack.add(curNode.getChildren().get(i));
      }

      // If we don't have an Id then we exclude this node from being added as a child to the parent.
      // The only known occurrence of this is the empty root node used to aggregate multiple selected objects.
      if (curNode.getData().getId().isEmpty()) {
        continue;
      }

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
    myGlobalTotal = 0;
    // how much time was spent doing work directly in this call stack path
    double self = 0;

    // The node that is at the top of the call stack, e.g if the call stack looks like B [0..30] -> B [1..20],
    // then the second method can't be outerSoFarByParent.
    // It's used to exclude nodes which aren't at the top of the
    // call stack from the total time calculation.
    // When multiple threads with the same ID are selected, the nodes are merged. When this happens nodes may be interlaced between
    // each of the threads. As such we keep a mapping of outer so far by parents to keep the book keeping done properly.
    HashMap<CaptureNode, CaptureNode> outerSoFarByParent = new HashMap<>();

    // myNodes is sorted by CaptureNode#getStart() in increasing order,
    // if they are equal then ancestor comes first
    for (CaptureNode node : myNodes) {
      // We use the root node to distinguish if two nodes share the same tree. In the event of multi-select we want to compute the bottom
      // up calculation independently for each tree then sum them after the fact.
      // TODO(153306735): Cache the root calculation, otherwise our update algorithm is going to be O(n*depth) instead of O(n)
      CaptureNode root = node.findRootNode();
      CaptureNode outerSoFar = outerSoFarByParent.getOrDefault(root, null);
      if (outerSoFar == null || node.getEnd() > outerSoFar.getEnd()) {
        if (outerSoFar != null) {
          // |outerSoFarByParent| is at the top of the call stack
          myGlobalTotal += getIntersection(range, outerSoFar, ClockType.GLOBAL);
        }
        outerSoFarByParent.put(root, node);
      }

      self += getIntersection(range, node, ClockType.GLOBAL);
      for (CaptureNode child : node.getChildren()) {
        self -= getIntersection(range, child, ClockType.GLOBAL);
      }
    }

    for(CaptureNode outerSoFar : outerSoFarByParent.values()) {
      // |outerSoFarByParent| is at the top of the call stack
      myGlobalTotal += getIntersection(range, outerSoFar, ClockType.GLOBAL);
    }
    myGlobalChildrenTotal = myGlobalTotal - self;
  }

  @NotNull
  @Override
  public CaptureNodeModel getMethodModel() {
    if (myIsRoot) {
      // Return a sample entry for the root.
      return new SingleNameModel("");
    }
    return myPathNodes.get(0).getData();
  }

  @Override
  public CaptureNode.FilterType getFilterType() {
    if (myIsRoot) {
      return CaptureNode.FilterType.MATCH;
    }
    return myPathNodes.get(0).getFilterType();
  }
}
