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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

/**
 * The model for a JTree that updates for a given range. It uses a CpuTreeNode as it's backing tree.
 */
abstract class CpuTreeModel<T extends CpuTreeNode<T>> extends DefaultTreeModel {

  private final Range myRange;
  private final Range myCurrentRange;
  private final AspectObserver myAspectObserver;

  public CpuTreeModel(@NotNull Range range, @NotNull T node) {
    super(new DefaultMutableTreeNode(node));
    myRange = range;
    myCurrentRange = new Range();
    myAspectObserver = new AspectObserver();
    myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::rangeChanged);
    rangeChanged();
  }

  public void rangeChanged() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)getRoot();

    List<Range> diffs = new LinkedList<>();
    // Add all the newly added ranges.
    diffs.addAll(myRange.subtract(myCurrentRange));
    // Add the ranges we don't have anymore
    diffs.addAll(myCurrentRange.subtract(myRange));

    update(root, myRange, diffs);

    myCurrentRange.set(myRange);
  }

  public boolean changes(T data, List<Range> ranges) {
    for (Range diff : ranges) {
      if (data.inRange(diff)) {
        return true;
      }
    }
    return false;
  }

  private void update(DefaultMutableTreeNode node, Range range, List<Range> ranges) {
    T data = (T)node.getUserObject();

    if (changes(data, ranges)) {
      Enumeration e = node.children();
      Map<T, DefaultMutableTreeNode> children = new HashMap<>();
      while (e.hasMoreElements()) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode)e.nextElement();
        children.put((T)child.getUserObject(), child);
      }
      Set<T> actual = new HashSet<>();
      for (T child : data.getChildren()) {
        if (child.inRange(range)) {
          actual.add(child);
          DefaultMutableTreeNode existing = children.get(child);
          if (existing == null) {
            existing = new DefaultMutableTreeNode(child);
            insertNodeInto(existing, node, node.getChildCount());
          }
          update(existing, range, ranges);
        } else {
          child.reset();
        }
      }
      for (Map.Entry<T, DefaultMutableTreeNode> entry : children.entrySet()) {
        if (!actual.contains(entry.getKey())) {
          removeNodeFromParent(entry.getValue());
        }
      }
      data.update(range);
      nodeChanged(node);
    }
  }

  @NotNull
  protected Range getRange() {
    return myRange;
  }

  public boolean isEmpty() {
    T data = (T)((DefaultMutableTreeNode)getRoot()).getUserObject();
    return data.getTotal() == 0;
  }

  abstract void expand(@NotNull DefaultMutableTreeNode node);
}
