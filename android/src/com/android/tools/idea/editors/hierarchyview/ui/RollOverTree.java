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
package com.android.tools.idea.editors.hierarchyview.ui;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Tree with roll over effect
 */
public class RollOverTree extends Tree {

  private final List<TreeHoverListener> mHoverListeners = Lists.newArrayList();

  @Nullable
  private TreePath mHoverPath = null;

  public RollOverTree() {
    this(getDefaultTreeModel());
  }

  public RollOverTree(TreeNode root) {
    this(new DefaultTreeModel(root, false));
  }

  public RollOverTree(TreeModel treemodel) {
    super(treemodel);
    setOpaque(false);

    RollOverAdapter adapter = new RollOverAdapter();
    addMouseListener(adapter);
    addMouseMotionListener(adapter);
  }

  @Nullable
  public TreePath getPathForEvent(@NotNull MouseEvent e) {
    int row = getClosestRowForLocation(e.getX(), e.getY());
    Rectangle bounds = getRowBounds(row);
    if (bounds == null) {
      row = -1;
    } else {
      if (bounds.y + bounds.height < e.getY())   {
        row = -1;
      }
    }
    return row == -1 ? null : getPathForRow(row);
  }

  /**
   * Sets or clears the cell to shows the hover effect.
   */
  public void updateHoverPath(@Nullable TreePath hoverPath) {
    if (!Objects.equal(mHoverPath, hoverPath)) {
      repaint(mHoverPath);
      mHoverPath = hoverPath;
      repaint(mHoverPath);

      for (TreeHoverListener listener : mHoverListeners) {
        listener.onTreeCellHover(hoverPath);
      }
    }
  }

  private void repaint(@Nullable TreePath path) {
    if (path != null) {
      Rectangle r = getPathBounds(path);
      repaint(0, r.y, getWidth(), r.height);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (mHoverPath != null && !mHoverPath.equals(getSelectionPath())) {
      g.setColor(UIUtil.AQUA_SEPARATOR_BACKGROUND_COLOR);
      Rectangle r = getPathBounds(mHoverPath);
      g.fillRect(0, r.y, getWidth(), r.height);
    }
    super.paintComponent(g);
  }

  public void addTreeHoverListener(@NotNull TreeHoverListener listener) {
    mHoverListeners.add(listener);
  }

  public void removeTreeHoverListener(@NotNull TreeHoverListener listener) {
    mHoverListeners.remove(listener);
  }

  private class RollOverAdapter extends MouseAdapter {

    @Override
    public void mouseEntered(MouseEvent e) {
      updateHoverPath(getPathForEvent(e));
    }

    @Override
    public void mouseExited(MouseEvent e) {
      updateHoverPath(null);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      updateHoverPath(getPathForEvent(e));
    }
  }

  public interface TreeHoverListener {

    /**
     * Called when the hover cell changes
     */
    void onTreeCellHover(@Nullable TreePath path);
  }
}
