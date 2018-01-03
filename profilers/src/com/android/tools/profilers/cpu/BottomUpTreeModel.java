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

import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;

public class BottomUpTreeModel extends CpuTreeModel<BottomUpNode> {
  public BottomUpTreeModel(@NotNull Range range, @NotNull BottomUpNode node) {
    super(range, node);
  }

  @Override
  public void expand(@NotNull DefaultMutableTreeNode node) {
    BottomUpNode bottomUpNode = (BottomUpNode)node.getUserObject();

    for (int i = 0; i < node.getChildCount(); ++i) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
      BottomUpNode childBottomUp = (BottomUpNode)child.getUserObject();

      if (childBottomUp.buildChildren()) {
        loadChildren(child);
      }
    }

    // Some children of the |bottomUpNode| may be invisible in the current range, so build their children too
    for (BottomUpNode child: bottomUpNode.getChildren()) {
      child.buildChildren();
    }
    getAspect().changed(Aspect.TREE_MODEL);
  }

  private void loadChildren(@NotNull DefaultMutableTreeNode node) {
    BottomUpNode bottomUpNode = (BottomUpNode)node.getUserObject();

    for (BottomUpNode child: bottomUpNode.getChildren()) {
      if (child.inRange(getRange())) {
        child.update(getRange());
        insertNodeInto(new DefaultMutableTreeNode(child), node, 0);
      }
      else {
        child.reset();
      }
    }
  }
}
