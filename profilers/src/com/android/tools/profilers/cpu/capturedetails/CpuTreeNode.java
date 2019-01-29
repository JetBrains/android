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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class CpuTreeNode<T extends CpuTreeNode> {
  /**
   * References to {@link CaptureNode} that are used to extract information from to represent this CpuTreeNode,
   * such as {@link #getGlobalTotal()}, {@link #getGlobalChildrenTotal()}, etc...
   */
  protected final List<CaptureNode> myNodes = new ArrayList<>();
  private final List<T> myChildren = new ArrayList<>();

  private final String myId;
  protected double myGlobalTotal = 0;
  protected double myGlobalChildrenTotal = 0;
  protected double myThreadTotal = 0;
  protected double myThreadChildrenTotal = 0;

  public CpuTreeNode(String id) {
    myId = id;
  }

  public String getId() {
    return myId;
  }

  protected void addNode(@NotNull CaptureNode node) {
    myNodes.add(node);
  }

  protected void addNodes(@NotNull List<CaptureNode> nodes) {
    nodes.forEach(this::addNode);
  }

  @NotNull
  public List<CaptureNode> getNodes() {
    return myNodes;
  }

  protected void addChild(@NotNull T child) {
    myChildren.add(child);
  }

  public List<T> getChildren() {
    return myChildren;
  }

  public double getGlobalTotal() {
    return myGlobalTotal;
  }

  public double getThreadTotal() {
    return myThreadTotal;
  }

  public double getGlobalChildrenTotal() {
    return myGlobalChildrenTotal;
  }

  public double getSelf() {
    return getGlobalTotal() - getGlobalChildrenTotal();
  }

  public void update(@NotNull Range range) {
    myGlobalTotal = 0.0;
    myGlobalChildrenTotal = 0;
    myThreadTotal = 0.0;
    myThreadChildrenTotal = 0;

    for (CaptureNode node : myNodes) {
      myGlobalTotal += getIntersection(range, node, ClockType.GLOBAL);
      myThreadTotal += getIntersection(range, node, ClockType.THREAD);
      for (CaptureNode child : node.getChildren()) {
        myGlobalChildrenTotal += getIntersection(range, child, ClockType.GLOBAL);
        myThreadChildrenTotal += getIntersection(range, child, ClockType.THREAD);
      }
    }
  }

  protected static double getIntersection(@NotNull Range range, @NotNull CaptureNode node, @NotNull ClockType type) {
    Range intersection;
    if (type == ClockType.GLOBAL) {
      intersection = range.getIntersection(new Range(node.getStartGlobal(), node.getEndGlobal()));
    }
    else {
      intersection = range.getIntersection(new Range(node.getStartThread(), node.getEndThread()));
    }
    return intersection.isEmpty() ? 0.0 : intersection.getLength();
  }

  public boolean inRange(Range range) {
    return myNodes.stream().anyMatch(node -> node.getStart() < range.getMax() && range.getMin() < node.getEnd());
  }

  public void reset() {
    myGlobalTotal = 0;
    myGlobalChildrenTotal = 0;
    myThreadTotal = 0;
    myThreadChildrenTotal = 0;
  }

  @NotNull
  abstract public CaptureNodeModel getMethodModel();

  abstract public CaptureNode.FilterType getFilterType();

  public boolean isUnmatched() {
    return getFilterType() == CaptureNode.FilterType.UNMATCH;
  }
}
