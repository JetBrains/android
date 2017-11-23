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

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A top-down CPU usage tree. This is a node on that tree and represents all the calls that share the same callstack upto a point.
 * It's created from an execution tree by merging the nodes with the same path from the root.
 */
class TopDownNode extends CpuTreeNode<TopDownNode> {
  private static final String INVALID_ID = "";

  public TopDownNode(@NotNull CaptureNode node) {
    super(node.getData() == null ? INVALID_ID : node.getData().getId());
    addNode(node);

    // We're adding unmatched children separately, because we don't want to merge unmatched with matched,
    // i.e all merged children should have the same {@link CaptureNode.FilterType}.
    addChildren(node, false);
    addChildren(node, true);
  }

  /**
   * Adds children of {@param node} whose filter type matches to the flag {@param unmatched}.
   */
  private void addChildren(@NotNull CaptureNode node, boolean unmatched) {
    Map<String, TopDownNode> children = new TreeMap<>();
    for (CaptureNode child : node.getChildren()) {
      assert child.getData() != null;

      if (unmatched != child.isUnmatched()) {
        continue;
      }

      TopDownNode prev = children.get(child.getData().getId());
      TopDownNode other = new TopDownNode(child);
      if (prev == null) {
        children.put(child.getData().getId(), other);
        addChild(other);
      }
      else {
        prev.merge(other);
      }
    }
  }

  private void merge(TopDownNode other) {
    addNodes(other.getNodes());

    // We use a separate map for unmatched children, because we can not merge unmatched with matched,
    // i.e all merged children should have the same {@link CaptureNode.FilterType};
    Map<String, TopDownNode> children = new TreeMap<>();
    Map<String, TopDownNode> unmatchedChildren = new TreeMap<>();

    for (TopDownNode child : getChildren()) {
      if (child.isUnmatched()) {
        unmatchedChildren.put(child.getId(), child);
      } else {
        children.put(child.getId(), child);
      }
    }

    for (TopDownNode otherChild : other.getChildren()) {
      TopDownNode existing;

      if (otherChild.isUnmatched()) {
        existing = unmatchedChildren.get(otherChild.getId());
      } else {
        existing = children.get(otherChild.getId());
      }
      if (existing != null) {
        existing.merge(otherChild);
      }
      else {
        addChild(otherChild);
      }
    }
  }

  @Override
  public String getMethodName() {
    MethodModel data = getNodes().get(0).getData();
    return data == null ? "" : data.getName();
  }

  @Override
  public String getClassName() {
    MethodModel data = getNodes().get(0).getData();
    return data == null ? "" : data.getClassName();
  }

  @Override
  public String getSignature() {
    MethodModel data = getNodes().get(0).getData();
    return data == null ? "" : data.getSignature();
  }

  @Override
  public CaptureNode.FilterType getFilterType() {
    return getNodes().get(0).getFilterType();
  }
}
