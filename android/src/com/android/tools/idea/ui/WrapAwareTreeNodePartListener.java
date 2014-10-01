/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

public class WrapAwareTreeNodePartListener extends WrapAwareLinkMouseListenerBase {
  private final     TreeCellRenderer       myRenderer;
  //recalc optimization
  @Nullable private DefaultMutableTreeNode myLastHitNode;
  @Nullable private Component              myRenderedComp;

  public WrapAwareTreeNodePartListener(@NotNull TreeCellRenderer renderer) {
    myRenderer = renderer;
  }

  @Override
  protected Object getTagAt(@NotNull final MouseEvent e) {
    final JTree tree = (JTree)e.getSource();
    final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (myLastHitNode != treeNode) {
        myLastHitNode = treeNode;
        myRenderedComp = myRenderer.getTreeCellRendererComponent(tree, treeNode, false, false, treeNode.isLeaf(), -1, false);
      }

      if (myRenderedComp != null) {
        Rectangle bounds = tree.getPathBounds(path);
        if (bounds != null) {
          Component root = tree.getCellRenderer().getTreeCellRendererComponent(tree, treeNode, false, false, treeNode.isLeaf(), -1, false);
          root.setSize(bounds.getSize());
          root.doLayout();
          if (root instanceof WrapAwareColoredComponent) {
            WrapAwareColoredComponent component = (WrapAwareColoredComponent)root;
            int fragmentIndex = component.findFragmentAt(e.getX() - bounds.x, e.getY() - bounds.y);
            return fragmentIndex >= 0 ? component.getFragmentTag(fragmentIndex) : null;
          }
        }
      }
    }
    return null;
  }
}
