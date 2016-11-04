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

package com.android.tools.adtui.chart.hchart;

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AnimatedRange;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.Stack;

public class HTreeChart<T> extends AnimatedComponent implements MouseWheelListener, MouseListener {

  private static final String NO_HTREE = "No data available.";
  private static final String NO_RANGE = "X range width is zero: Please use a wider range.";
  ;
  private static final int BORDER_PLUS_PADDING = 2;
  private static final int ZOOM_FACTOR = 20;
  private static final String ACTION_ZOOM_IN = "zoom in";
  private static final String ACTION_ZOOM_OUT = "zoom out";
  private static final String ACTION_MOVE_LEFT = "move left";
  private static final String ACTION_MOVE_RIGHT = "move right";
  private static final int ACTION_MOVEMENT_FACTOR = 5;

  private Orientation mOrientation;
  @Nullable
  private HRenderer<T> mHRenderer;
  @Nullable
  private HNode<T> mRoot;
  @Nullable
  private Range mXRange;
  @NotNull
  private Range mYRange;
  @NotNull
  private Rectangle2D.Float mRect;

  public HTreeChart() {
    mRoot = new HNode<>();
    mRect = new Rectangle2D.Float();
    mYRange = new Range();
    addMouseWheelListener(this);
    mOrientation = HTreeChart.Orientation.TOP_DOWN;
    setFocusable(true);
    addMouseListener(this);
  }

  public HTreeChart(Orientation orientation) {
    this();
    mOrientation = orientation;
  }

  @Override
  protected void updateData() {
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (mRoot == null || mRoot.getChildren().size() == 0) {
      g.drawString(NO_HTREE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_HTREE),
                   dim.height / 2);
      return;
    }

    if (mXRange == null || mXRange.getLength() == 0) {
      g.drawString(NO_RANGE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_RANGE),
                   dim.height / 2);
      return;
    }

    // Render using a LIFO Stack instead of recursion to limit the depth of the Java call
    // stack.
    Stack<HNode<T>> stack = new Stack<>();
    stack.addAll(mRoot.getChildren());
    while (!stack.isEmpty()) {
      HNode<T> n = stack.pop();
      renderHNode(g, n);
      stack.addAll(n.getChildren());
    }
  }

  // This method is not thread-safe: It re-uses mRect.
  private void renderHNode(Graphics2D g, HNode<T> n) {

    // 1. Cull node to view Range.
    if (n.getStart() > getXRange().getMax() || n.getEnd() < getXRange().getMin()) {
      return;
    }

    // 2. Clip node.
    double leftEdge = rangeToPosition(n.getStart());
    if (leftEdge < 0) {
      leftEdge = 0;
    }
    double rightEdge = rangeToPosition(n.getEnd());
    if (rightEdge > getWidth()) {
      rightEdge = getWidth();
    }
    double width = rightEdge - leftEdge;

    // 3. Calculate node position and dimension.
    mRect.x = (float)leftEdge;
    mRect.y = (float)((mDefaultFontMetrics.getHeight() + BORDER_PLUS_PADDING) * n.getDepth()
                      - getYRange().getMin());
    mRect.width = (float)width - BORDER_PLUS_PADDING;
    mRect.height = mDefaultFontMetrics.getHeight();

    if (mOrientation == HTreeChart.Orientation.BOTTOM_UP) {
      mRect.y = (float)(getHeight() - mRect.y - mRect.getHeight());
    }

    // 4. Render node
    mHRenderer.render(g, n.getData(), mRect);
  }

  // This could be done with an Axis. But that seems overkill. A simple method will do for now.
  private double rangeToPosition(double v) {
    double translate = -getXRange().getMin();
    double scale = this.getWidth() / (getXRange().getMax() - getXRange().getMin());
    return (v + translate) * scale;
  }

  private double positionToRange(double x) {
    return x / getWidth() * getXRange().getLength() + getXRange().getMin();
  }

  public void setHRenderer(HRenderer<T> r) {
    this.mHRenderer = r;
    this.mHRenderer.setFont(AdtUiUtils.DEFAULT_FONT);
  }

  public void setHTree(@Nullable HNode<T> root) {
    this.mRoot = root;
  }

  public Range getXRange() {
    return mXRange;
  }

  public void setXRange(Range XRange) {
    mXRange = XRange;

    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), ACTION_ZOOM_IN);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ACTION_ZOOM_OUT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), ACTION_MOVE_LEFT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), ACTION_MOVE_RIGHT);

    getActionMap().put(ACTION_ZOOM_IN, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = mXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        mXRange.set(mXRange.getMin() + delta, mXRange.getMax() - delta);
      }
    });

    getActionMap().put(ACTION_ZOOM_OUT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = mXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        mXRange.set(mXRange.getMin() - delta, mXRange.getMax() + delta);
      }
    });

    getActionMap().put(ACTION_MOVE_LEFT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = mXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        mXRange.set(mXRange.getMin() - delta, mXRange.getMax() - delta);
      }
    });

    getActionMap().put(ACTION_MOVE_RIGHT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = mXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        mXRange.set(mXRange.getMin() + delta, mXRange.getMax() + delta);
      }
    });
  }

  public Range getYRange() {
    return mYRange;
  }

  public int getMaximumHeight() {
    if (mRoot == null) {
      return 0;
    }

    int maxDepth = -1;
    // Traverse the tree FIFO instead of recursion to limit the depth of the Java call
    // stack.
    Stack<HNode<T>> stack = new Stack<>();
    stack.addAll(mRoot.getChildren());
    while (!stack.isEmpty()) {
      HNode<T> n = stack.pop();
      if (n.getDepth() > maxDepth) {
        maxDepth = n.getDepth();
      }
      stack.addAll(n.getChildren());
    }
    maxDepth += 1;
    return (mDefaultFontMetrics.getHeight() + BORDER_PLUS_PADDING) * maxDepth;
  }

  // TODO we probably want to extract/abstract this logic out later so the zooming behavior
  // is consistent across components
  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    double cursorRange = positionToRange(e.getX());
    double leftDelta = (cursorRange - getXRange().getMin()) / ZOOM_FACTOR * e
      .getWheelRotation();
    double rightDelta = (getXRange().getMax() - cursorRange) / ZOOM_FACTOR * e
      .getWheelRotation();
    getXRange().setMin(getXRange().getMin() - leftDelta);
    getXRange().setMax(getXRange().getMax() + rightDelta);
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    if (!hasFocus()) {
      requestFocusInWindow();
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
  }

  @Override
  public void mouseReleased(MouseEvent e) {
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {

  }

  public enum Orientation {TOP_DOWN, BOTTOM_UP}
}