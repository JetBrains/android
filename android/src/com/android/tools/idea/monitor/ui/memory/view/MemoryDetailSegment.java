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
package com.android.tools.idea.monitor.ui.memory.view;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.memory.model.MemoryInfoTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

/**
 * Segment implementation for the memory profiler detailed view.
 */
public class MemoryDetailSegment extends BaseSegment {
  private static final String SEGMENT_NAME = "Memory Details";

  private static final Color NEGATIVE_COLOR = new JBColor(new Color(0x33FF0000, true), new Color(0x33FF6464, true));
  private static final Color POSITIVE_COLOR = new JBColor(new Color(0x330000FF, true), new Color(0x33589df6, true));

  @NotNull
  private final MemoryInfoTreeNode mRoot;

  private JComponent mColumnTree;

  private JTree mTree;

  private DefaultTreeModel mTreeModel;

  public MemoryDetailSegment(@NotNull Range timeRange, @NotNull MemoryInfoTreeNode root) {
    super(SEGMENT_NAME, timeRange);
    mRoot = root;
  }

  /**
   * Requests the tree model to perform a reload on the input node.
   */
  public void refreshNode(@NotNull MemoryInfoTreeNode node) {
    mTreeModel.reload(node);
  }

  public boolean getExpandState(@NotNull MemoryInfoTreeNode node) {
    return mTree.isExpanded(new TreePath(mTreeModel.getPathToRoot(node)));
  }

  public void setExpandState(@NotNull MemoryInfoTreeNode node, boolean expand) {
    if (expand) {
      mTree.expandPath(new TreePath(mTreeModel.getPathToRoot(node)));
    } else {
      mTree.collapsePath(new TreePath(mTreeModel.getPathToRoot(node)));
    }
  }

  public void insertNode(@NotNull MemoryInfoTreeNode parent, @NotNull MemoryInfoTreeNode child) {
    mTreeModel.insertNodeInto(child, parent, parent.getChildCount());
  }

  @Override
  protected boolean hasLeftContent() {
    return false;
  }

  @Override
  protected boolean hasRightContent() {
    return false;
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    panel.add(mColumnTree, BorderLayout.CENTER);
  }

  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {
    mTreeModel = new DefaultTreeModel(mRoot);
    mTree = new Tree(mTreeModel);
    mTree.setRootVisible(false);
    mTree.setShowsRootHandles(true);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(mTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Class")
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new MemoryInfoColumnRenderer(0, mRoot)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Count")
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new MemoryInfoColumnRenderer(1, mRoot)));

    mColumnTree = builder.build();
  }

  /**
   * A simple cell renderer for columns that renders a bar indicating the deltas along with the node's content.
   */
  private static class MemoryInfoColumnRenderer extends ColoredTreeCellRenderer {
    @NotNull
    private final MemoryInfoHealthBar mHealthBar;

    @NotNull
    private final MemoryInfoTreeNode mRoot;

    private final int mColumnIndex;

    private MemoryInfoColumnRenderer(int index, @NotNull MemoryInfoTreeNode root) {
      mHealthBar = new MemoryInfoHealthBar();
      mColumnIndex = index;
      mRoot = root;

      if (mColumnIndex > 0) {
        setLayout(new BorderLayout());
        add(mHealthBar, BorderLayout.CENTER);
      }
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof MemoryInfoTreeNode) {
        MemoryInfoTreeNode node = (MemoryInfoTreeNode)value;
        switch (mColumnIndex) {
          case 0:
            append(node.getName());
            break;
          case 1:
            append(String.valueOf(node.getCount()));
            break;
        }

        if (mHealthBar != null) {
          mHealthBar.setDelta((float)node.getCount() / mRoot.getCount());
          mHealthBar.setPercentage((float)node.getCount() / mRoot.getCount());
        }
      }
    }
  }

  private static class MemoryInfoHealthBar extends JComponent {
    private float mDelta;
    private float mPercentage;

    private void setDelta(float delta) {
      mDelta = delta;
    }

    private void setPercentage(float percent) {
      mPercentage = percent;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      Dimension dim = getSize();
      if (mDelta > 0) {
        g.setColor(NEGATIVE_COLOR);
      } else {
        g.setColor(POSITIVE_COLOR);
      }

      g.fillRect(0, 0, (int)(dim.width * mPercentage), dim.height);
    }
  }
}