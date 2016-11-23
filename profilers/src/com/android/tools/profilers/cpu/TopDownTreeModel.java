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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedTreeModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

/**
 * The model for a JTree that updates for a given range. It uses a TopDownNode as it's backing tree.
 */
class TopDownTreeModel extends DefaultTreeModel implements RangedTreeModel {

  private Range myRange;

  public TopDownTreeModel(TopDownNode node) {
    super(new DefaultMutableTreeNode(node));
    myRange = new Range();
  }

  @Override
  public void update(@NotNull Range range) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)getRoot();

    List<Range> diffs = new LinkedList<>();
    // Add all the newly added ranges.
    diffs.addAll(range.subtract(myRange));
    // Add the ranges we don't have anymore
    diffs.addAll(myRange.subtract(range));

    update(root, range, diffs);

    myRange.set(range);
  }

  public boolean changes(TopDownNode data, List<Range> ranges) {
    for (Range diff : ranges) {
      if (data.inRange(diff)) {
        return true;
      }
    }
    return false;
  }

  private void update(DefaultMutableTreeNode node, Range range, List<Range> ranges) {
    TopDownNode data = (TopDownNode)node.getUserObject();
    if (changes(data, ranges)) {
      Enumeration e = node.children();
      Map<String, DefaultMutableTreeNode> children = new HashMap<>();
      while (e.hasMoreElements()) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode)e.nextElement();
        children.put(((TopDownNode)child.getUserObject()).getId(), child);
      }
      Set<String> actual = new TreeSet<>();
      for (TopDownNode child : data.getChildren()) {
        if (child.inRange(range)) {
          actual.add(child.getId());
          DefaultMutableTreeNode existing = children.get(child.getId());
          if (existing == null) {
            existing = new DefaultMutableTreeNode(child);
            insertNodeInto(existing, node, 0);
          }
          update(existing, range, ranges);
        } else {
          child.reset();
        }
      }
      for (Map.Entry<String, DefaultMutableTreeNode> entry : children.entrySet()) {
        if (!actual.contains(entry.getKey())) {
          removeNodeFromParent(entry.getValue());
        }
      }
      data.update(range);
      nodeChanged(node);
    }
  }
}
