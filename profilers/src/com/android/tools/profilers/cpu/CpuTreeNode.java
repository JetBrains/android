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

import java.util.ArrayList;
import java.util.List;

public abstract class CpuTreeNode<T extends CpuTreeNode> {
  /**
   * References to {@link CaptureNode} that are used to extract information from to represent this CpuTreeNode,
   * such as {@link #getTotal()}, {@link #getChildrenTotal()}, etc...
   */
  protected final List<CaptureNode> myNodes = new ArrayList<>();
  private final List<T> myChildren = new ArrayList<>();

  private final String myId;
  protected double myTotal = 0;
  protected double myChildrenTotal = 0;

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

  protected List<T> getChildren() {
    return myChildren;
  }

  public double getTotal() {
    return myTotal;
  }

  public double getChildrenTotal() {
    return myChildrenTotal;
  }

  public double getSelf() {
    return getTotal() - getChildrenTotal();
  }

  public void update(@NotNull Range range) {
    myTotal = 0.0;
    myChildrenTotal = 0;

    for (CaptureNode node : myNodes) {
      myTotal += getIntersection(range, node);
      for (HNode<MethodModel> child : node.getChildren()) {
        myChildrenTotal += getIntersection(range, child);
      }
    }
  }

  protected static double getIntersection(@NotNull Range range, @NotNull HNode<MethodModel> node) {
    Range intersection = range.getIntersection(new Range(node.getStart(), node.getEnd()));
    return intersection.isEmpty() ? 0.0 : intersection.getLength();
  }

  public boolean inRange(Range range) {
    return myNodes.stream().anyMatch(node -> node.getStart() < range.getMax() && range.getMin() < node.getEnd());
  }

  public void reset() {
    myTotal = 0;
    myChildrenTotal = 0;
  }

  abstract public String getMethodName();

  abstract public String getClassName();

  abstract public String getSignature();
}
