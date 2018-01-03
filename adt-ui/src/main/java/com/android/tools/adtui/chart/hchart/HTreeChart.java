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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * A chart which renders nodes using a horizontal flow. That is, while normal trees are vertical,
 * rendering nested rows top-to-bottom, this chart renders nested columns left-to-right.
 *
 * @param <N> The type of the node used by this tree chart
 */
public class HTreeChart<N extends HNode<N>> extends AnimatedComponent {

  private static final String NO_HTREE = "No data available.";
  private static final String NO_RANGE = "X range width is zero: Please use a wider range.";
  private static final int ZOOM_FACTOR = 20;
  private static final String ACTION_ZOOM_IN = "zoom in";
  private static final String ACTION_ZOOM_OUT = "zoom out";
  private static final String ACTION_MOVE_LEFT = "move left";
  private static final String ACTION_MOVE_RIGHT = "move right";
  private static final int ACTION_MOVEMENT_FACTOR = 5;
  private static final int BORDER_PLUS_PADDING = 2;
  private static final int INITIAL_Y_POSITION = 0;
  private static final int HEIGHT_PADDING = 15;

  private final Orientation myOrientation;

  @Nullable
  private HRenderer<N> myRenderer;

  @Nullable
  private N myRoot;

  @NotNull
  private final Range myXRange;

  /**
   * The X range that myXRange could possibly be. Any changes to X range should be limited within it.
   */
  @NotNull
  private final Range myGlobalXRange;

  @NotNull
  private final Range myYRange;

  @NotNull
  private final List<Rectangle2D.Float> myRectangles;

  @NotNull
  private final List<N> myNodes;

  private boolean myRootVisible;

  @Nullable
  private N myFocusedNode;

  @NotNull
  private final List<Rectangle2D.Float> myDrawnRectangles;

  @NotNull
  private final List<N> myDrawnNodes;

  @NotNull
  private final HTreeChartReducer<N> myReducer;

  @Nullable
  private Image myCanvas;

  /**
   * If true, the next render pass will forcefully rebuild this chart's canvas (an expensive
   * operation which doesn't have to be done too often as usually the contents are static)
   */
  private boolean myDataUpdated;

  private int myCachedMaxHeight;

  /**
   * Create a Horizontal Tree Chart.
   * @param globalXRange the bounding range of chart visible area, if not null.
   * @param viewXRange the range of the chart's visible area.
   */
  @VisibleForTesting
  public HTreeChart(@Nullable Range globalXRange, @NotNull Range viewXRange, Orientation orientation, @NotNull HTreeChartReducer<N> reducer) {
    myRectangles = new ArrayList<>();
    myNodes = new ArrayList<>();
    myDrawnNodes = new ArrayList<>();
    myDrawnRectangles = new ArrayList<>();
    myGlobalXRange = globalXRange != null ? globalXRange : new Range(-Double.MAX_VALUE, Double.MAX_VALUE);
    myXRange = viewXRange;
    myRoot = null;
    myReducer = reducer;
    myYRange = new Range(INITIAL_Y_POSITION, INITIAL_Y_POSITION);
    myOrientation = orientation;
    myRootVisible = true;

    setFocusable(true);
    initializeInputMap();
    initializeMouseEvents();
    setFont(AdtUiUtils.DEFAULT_FONT);
    myXRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::changed);
    myYRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::changed);
    changed();
  }

  public HTreeChart(@Nullable Range globalXRange, @NotNull Range viewXRange, Orientation orientation) {
    this(globalXRange, viewXRange, orientation, new DefaultHTreeChartReducer<>());
  }

  public void setRootVisible(boolean rootVisible) {
    myRootVisible = rootVisible;
    changed();
  }

  /**
   * Normally, the focused node is set by mouse hover. However, for tests, it can be a huge
   * convenience to set this directly.
   *
   * It is up to the caller to make sure that the node specified here actually belongs to this
   * chart. Otherwise, the call will have no effect.
   */
  @VisibleForTesting
  public void setFocusedNode(@Nullable N node) {
    myFocusedNode = node;
  }

  private void changed() {
    myDataUpdated = true;
    myCachedMaxHeight = calculateMaximumHeight();
    opaqueRepaint();
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    long startTime = System.nanoTime();
    if (myDataUpdated) {
      // Nulling out the canvas will trigger a render pass, below
      updateNodesAndClearCanvas();
      myDataUpdated = false;
    }
    g.setFont(getFont());
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (myRoot == null || myRoot.getChildCount() == 0) {
      g.drawString(NO_HTREE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_HTREE),
                   dim.height / 2);
      return;
    }

    if (myXRange.getLength() == 0) {
      g.drawString(NO_RANGE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_RANGE),
                   dim.height / 2);
      return;
    }

    if (myCanvas == null || ImageUtil.getUserHeight(myCanvas) != dim.height || ImageUtil.getUserWidth(myCanvas) != dim.width) {
      redrawToCanvas(dim);
    }
    UIUtil.drawImage(g, myCanvas, 0, 0, null);
    addDebugInfo("Draw time %.2fms", (System.nanoTime() - startTime) / 1e6);
    addDebugInfo("# of nodes %d", myNodes.size());
    addDebugInfo("# of reduced nodes %d", myDrawnNodes.size());
  }

  private void redrawToCanvas(@NotNull Dimension dim) {
    final Graphics2D g;
    if (myCanvas != null && ImageUtil.getUserWidth(myCanvas) >= dim.width && ImageUtil.getUserHeight(myCanvas) >= dim.height) {
      g = (Graphics2D)myCanvas.getGraphics();
      g.setColor(getBackground());
      g.fillRect(0, 0, dim.width, dim.height);
    } else {
      myCanvas = UIUtil.createImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
      g = (Graphics2D)myCanvas.getGraphics();
    }
    g.setFont(getFont());
    myDrawnNodes.clear();
    myDrawnNodes.addAll(myNodes);

    myDrawnRectangles.clear();
    // Transform
    for (Rectangle2D.Float rect : myRectangles) {
      Rectangle2D.Float newRect = new Rectangle2D.Float();
      newRect.x = rect.x * (float)dim.getWidth();
      newRect.y = rect.y;
      newRect.width = Math.max(0, rect.width * (float)dim.getWidth() - BORDER_PLUS_PADDING);
      newRect.height = rect.height;

      if (myOrientation == HTreeChart.Orientation.BOTTOM_UP) {
        newRect.y = (float)(dim.getHeight() - newRect.y - newRect.getHeight());
      }

      myDrawnRectangles.add(newRect);
    }

    myReducer.reduce(myDrawnRectangles, myDrawnNodes);

    assert myDrawnRectangles.size() == myDrawnNodes.size();
    assert myRenderer != null;
    for (int i = 0; i < myDrawnNodes.size(); ++i) {
      N node = myDrawnNodes.get(i);
      myRenderer.render(g, node, myDrawnRectangles.get(i), node == myFocusedNode);
    }

    g.dispose();
  }

  private void updateNodesAndClearCanvas() {
    myNodes.clear();
    myRectangles.clear();
    myCanvas = null;
    if (myRoot == null) {
      return;
    }

    if (inRange(myRoot)) {
      myNodes.add(myRoot);
      myRectangles.add(createRectangle(myRoot));
    }

    int head = 0;
    while (head < myNodes.size()) {
      N curNode = myNodes.get(head++);

      for (int i = 0; i < curNode.getChildCount(); ++i) {
        N child = curNode.getChildAt(i);
        if (inRange(child)) {
          myNodes.add(child);
          myRectangles.add(createRectangle(child));
        }
      }
    }
    if (!myRootVisible && !myNodes.isEmpty()) {
      myNodes.remove(0);
      myRectangles.remove(0);
    }
  }

  private boolean inRange(@NotNull N node) {
    return node.getStart() <= myXRange.getMax() && node.getEnd() >= myXRange.getMin();
  }

  @NotNull
  private Rectangle2D.Float createRectangle(@NotNull N node) {
    float left = (float)Math.max(0, (node.getStart() - myXRange.getMin()) / myXRange.getLength());
    float right = (float)Math.min(1, (node.getEnd() - myXRange.getMin()) / myXRange.getLength());
    Rectangle2D.Float rect = new Rectangle2D.Float();
    rect.x = left;
    rect.y = (float)((mDefaultFontMetrics.getHeight() + BORDER_PLUS_PADDING) * node.getDepth()
                     - getYRange().getMin());
    rect.width = right - left;
    rect.height = mDefaultFontMetrics.getHeight();
    return rect;
  }

  private double positionToRange(double x) {
    return x / getWidth() * myXRange.getLength() + myXRange.getMin();
  }

  public void setHRenderer(@NotNull HRenderer<N> r) {
    this.myRenderer = r;
  }

  public void setHTree(@Nullable N root) {
    this.myRoot = root;
    changed();
  }

  @Nullable
  public N getNodeAt(Point point) {
    if (point != null) {
      for (int i = 0; i < myDrawnNodes.size(); ++i) {
        if (contains(myDrawnRectangles.get(i), point)) {
          return myDrawnNodes.get(i);
        }
      }
    }
    return null;
  }

  private static boolean contains(@NotNull Rectangle2D rectangle, @NotNull Point p) {
    return rectangle.getMinX() <= p.getX() && p.getX() <= rectangle.getMaxX() &&
           rectangle.getMinY() <= p.getY() && p.getY() <= rectangle.getMaxY();
  }

  @NotNull
  public Orientation getOrientation() {
    return myOrientation;
  }

  private void initializeInputMap() {
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), ACTION_ZOOM_IN);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ACTION_ZOOM_OUT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), ACTION_MOVE_LEFT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), ACTION_MOVE_RIGHT);

    getActionMap().put(ACTION_ZOOM_IN, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = myXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        myXRange.set(myXRange.getMin() + delta, myXRange.getMax() - delta);
      }
    });

    getActionMap().put(ACTION_ZOOM_OUT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = myXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        myXRange.set(Math.max(myGlobalXRange.getMin(), myXRange.getMin() - delta), Math.min(myGlobalXRange.getMax(), myXRange.getMax() + delta));
      }
    });

    getActionMap().put(ACTION_MOVE_LEFT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = myXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        delta = Math.min(myXRange.getMin() - myGlobalXRange.getMin(), delta);
        myXRange.shift(-delta);
      }
    });

    getActionMap().put(ACTION_MOVE_RIGHT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = myXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        delta = Math.min(myGlobalXRange.getMax() - myXRange.getMax(), delta);
        myXRange.shift(delta);
      }
    });
  }

  private void initializeMouseEvents() {
    MouseAdapter adapter = new MouseAdapter() {
      private Point myLastPoint;

      @Override
      public void mouseMoved(MouseEvent e) {
        N node = getNodeAt(e.getPoint());
        if (node != myFocusedNode) {
          myDataUpdated = true;
          myFocusedNode = node;
          opaqueRepaint();
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (!hasFocus()) {
          requestFocusInWindow();
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        myLastPoint = e.getPoint();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        // First, handle Y range.
        // The height of the contents we can show, including those not currently shown because of vertical scrollbar's position.
        int contentHeight = getMaximumHeight();
        // The height of the GUI component to draw the contents.
        int viewHeight = getHeight();
        double deltaY = e.getPoint().y - myLastPoint.y;
        deltaY = getOrientation() == Orientation.BOTTOM_UP ? deltaY : -deltaY;
        if (myYRange.getMin() + deltaY < INITIAL_Y_POSITION) {
          // User attempts to drag the chart's head (the outermost frame on call stacks) away from the boundary. No.
          deltaY = INITIAL_Y_POSITION - myYRange.getMin();
        }
        else if (myYRange.getMin() + viewHeight + deltaY > contentHeight) {
          // User attempts to drag the chart's toe (the innermost frame on call stacks) away from the boundary. No.
          // Note that the chart may be taller than the stacks, so we need to limit the delta.
          deltaY = Math.max(0, contentHeight - viewHeight - myYRange.getMin());
        }
        getYRange().shift(deltaY);

        // Second, handle X Range.
        double deltaX = e.getPoint().x - myLastPoint.x;
        double deltaXToShift = myXRange.getLength() / getWidth() * -deltaX;
        if (deltaXToShift > 0) {
          // User attempts to move the chart towards left to view the area to the right.
          deltaXToShift = Math.min(myGlobalXRange.getMax() - myXRange.getMax(), deltaXToShift);
        }
        else if (deltaXToShift < 0) {
          // User attempts to move the chart towards right to view the area to the left.
          deltaXToShift = Math.max(myGlobalXRange.getMin() - myXRange.getMin(), deltaXToShift);
        }
        myXRange.shift(deltaXToShift);

        myLastPoint = e.getPoint();
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        double cursorRange = positionToRange(e.getX());
        double leftDelta = (cursorRange - myXRange.getMin()) / ZOOM_FACTOR * e.getWheelRotation();
        double rightDelta = (myXRange.getMax() - cursorRange) / ZOOM_FACTOR * e.getWheelRotation();
        myXRange.set(Math.max(myGlobalXRange.getMin(), myXRange.getMin() - leftDelta),
                     Math.min(myGlobalXRange.getMax(), myXRange.getMax() + rightDelta));
      }
    };
    addMouseWheelListener(adapter);
    addMouseListener(adapter);
    addMouseMotionListener(adapter);
  }

  public Range getYRange() {
    return myYRange;
  }

  public int getMaximumHeight() {
    return myCachedMaxHeight;
  }

  private int calculateMaximumHeight() {
    if (myRoot == null) {
      return 0;
    }

    int maxDepth = -1;
    Queue<N> queue = new LinkedList<>();
    queue.add(myRoot);

    while (!queue.isEmpty()) {
      N n = queue.poll();
      if (n.getDepth() > maxDepth) {
        maxDepth = n.getDepth();
      }

      for (int i = 0; i < n.getChildCount(); ++i) {
        queue.add(n.getChildAt(i));
      }
    }
    maxDepth += 1;
    // The HEIGHT_PADDING is for the chart's toe (the innermost frame on call stacks).
    // We have this because the padding near the chart's head (the outermost frame on call stacks)
    // is there because the root node of the tree is invisible.
    return (mDefaultFontMetrics.getHeight() + BORDER_PLUS_PADDING) * maxDepth + HEIGHT_PADDING;
  }

  public enum Orientation {TOP_DOWN, BOTTOM_UP}
}