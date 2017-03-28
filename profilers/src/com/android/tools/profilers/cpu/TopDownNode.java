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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A top-down CPU usage tree. This is a node on that tree and represents all the calls that share the same callstack upto a point.
 * It's created from an execution tree by merging the nodes with the same path from the root.
 */
class TopDownNode {
  private final List<HNode<MethodModel>> myNodes;
  private final List<TopDownNode> myChildren;
  private final String myId;
  private double myTotal;

  public TopDownNode(@NotNull HNode<MethodModel> node) {
    myNodes = new LinkedList<>();
    myChildren = new ArrayList<>();
    myId = node.getData().getId();
    myNodes.add(node);

    Map<String, TopDownNode> children = new TreeMap<>();
    for (HNode<MethodModel> child : node.getChildren()) {
      TopDownNode prev = children.get(child.getData().getId());
      TopDownNode other = new TopDownNode(child);
      if (prev == null) {
        children.put(child.getData().getId(), other);
        myChildren.add(other);
      }
      else {
        prev.merge(other);
      }
    }
  }

  private void merge(TopDownNode other) {
    myNodes.addAll(other.myNodes);
    Map<String, TopDownNode> children = new TreeMap<>();
    for (TopDownNode child : myChildren) {
      children.put(child.myId, child);
    }
    for (TopDownNode otherChild : other.myChildren) {
      TopDownNode existing = children.get(otherChild.myId);
      if (existing != null) {
        existing.merge(otherChild);
      }
      else {
        myChildren.add(otherChild);
      }
    }
  }

  public String getId() {
    return myId;
  }

  public List<TopDownNode> getChildren() {
    return myChildren;
  }

  public boolean inRange(Range range) {
    return Iterables.any(myNodes, node -> node.getStart() < range.getMax() && range.getMin() < node.getEnd());
  }

  public String getMethodName() {
    MethodModel data = myNodes.get(0).getData();
    return data.getName();
  }

  public String getPackage() {
    MethodModel data = myNodes.get(0).getData();
    return data.getNameSpace();
  }

  public double getTotal() {
    return myTotal;
  }

  public double getSelf() {
    return getTotal() - getChildrenTotal();
  }

  public double getChildrenTotal() {
    double total = 0;
    for (TopDownNode child : myChildren) {
      total += child.getTotal();
    }
    return total;
  }

  public void update(Range range) {
    myTotal = 0.0;
    for (HNode<MethodModel> node : myNodes) {
      Range intersection = range.getIntersection(new Range(node.getStart(), node.getEnd()));
      myTotal += intersection.isEmpty() ? 0 : intersection.getLength();
    }
  }

  public void reset() {
    myTotal = 0.0;
  }
}
