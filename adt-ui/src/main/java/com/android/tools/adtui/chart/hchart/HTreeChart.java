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
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import com.intellij.util.ui.StartupUiUtil;
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
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @VisibleForTesting
  static final int PADDING = 1;
  private static final int INITIAL_Y_POSITION = 0;
  @VisibleForTesting
  static final int HEIGHT_PADDING = 15;
  private static final int MOUSE_WHEEL_SCROLL_FACTOR = 8;

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

  private boolean myNodeSelectionEnabled;

  @Nullable
  private N mySelectedNode;

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
   * Height of a tree node in pixels. If not set, we use the default font height.
   */
  private final int myCustomNodeHeightPx;

  /**
   * Vertical and horizontal padding in pixels between tree nodes.
   */
  private final int myNodeXPaddingPx;
  private final int myNodeYPaddingPx;

  /**
   * Creates a Horizontal Tree Chart.
   */
  private HTreeChart(@NotNull Builder<N> builder) {
    myGlobalXRange = builder.myGlobalXRange;
    myXRange = builder.myXRange;
    myRoot = builder.myRoot;
    myReducer = builder.myReducer;
    myRenderer = builder.myRenderer;
    myOrientation = builder.myOrientation;
    myRootVisible = builder.myRootVisible;
    myNodeSelectionEnabled = builder.myNodeSelectionEnabled;
    myCustomNodeHeightPx = builder.myCustomNodeHeightPx;
    myNodeXPaddingPx = builder.myNodeXPaddingPx;
    myNodeYPaddingPx = builder.myNodeYPaddingPx;

    myYRange = new Range(INITIAL_Y_POSITION, INITIAL_Y_POSITION);
    myRectangles = new ArrayList<>();
    myNodes = new ArrayList<>();
    myDrawnNodes = new ArrayList<>();
    myDrawnRectangles = new ArrayList<>();
    mySelectedNode = null;

    setFocusable(true);
    initializeInputMap();
    initializeMouseEvents();
    setFont(AdtUiUtils.DEFAULT_FONT);
    myXRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::changed);
    myYRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::changed);
    changed();
  }

  /**
   * Normally, the focused node is set by mouse hover. However, for tests, it can be a huge
   * convenience to set this directly.
   * <p>
   * It is up to the caller to make sure that the node specified here actually belongs to this
   * chart. Otherwise, the call will have no effect.
   */
  @VisibleForTesting
  public void setFocusedNode(@Nullable N node) {
    myFocusedNode = node;
  }

  /**
   * Updates the selected node. This is called by mouse click event handler and also from other instances of HTreeChart selects a node and
   * wants to update the (un)selected state of this instance.
   *
   * @param selectedNode the new selected node, or null if no node is being selected.
   */
  public void setSelectedNode(@Nullable N selectedNode) {
    if (selectedNode != mySelectedNode) {
      myDataUpdated = true;
      mySelectedNode = selectedNode;
    }
  }

  @VisibleForTesting
  @Nullable
  public N getSelectedNode() {
    return mySelectedNode;
  }

  @Nullable
  public N getFocusedNode() {
    return myFocusedNode;
  }

  private void changed() {
    myDataUpdated = true;
    myCachedMaxHeight = calculateMaximumHeight();
    // Update preferred size using calculated height to make sure containers of this chart account for the height change during layout.
    setPreferredSize(new Dimension(getPreferredSize().width, myCachedMaxHeight));
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
    if (myCanvas == null || (ImageUtil.getUserWidth(myCanvas) < dim.width || ImageUtil.getUserHeight(myCanvas) < dim.height)) {
      // Note: We intentionally create an RGB image, not an ARGB image, because this allows nodes
      // to render their text clearly (ARGB prevents LCD rendering from working).
      myCanvas = ImageUtil.createImage(dim.width, dim.height, BufferedImage.TYPE_INT_RGB);
    }
    final Graphics2D g = (Graphics2D)myCanvas.getGraphics();
    g.setColor(getBackground());
    g.fillRect(0, 0, dim.width, dim.height);

    UISettings.setupAntialiasing(g);
    g.setFont(getFont());

    myDrawnNodes.clear();
    myDrawnNodes.addAll(myNodes);

    myDrawnRectangles.clear();
    // Transform
    for (Rectangle2D.Float rect : myRectangles) {
      Rectangle2D.Float newRect = new Rectangle2D.Float();
      newRect.x = rect.x * (float)dim.getWidth();
      newRect.y = rect.y;
      newRect.width = Math.max(0, rect.width * (float)dim.getWidth() - myNodeXPaddingPx);
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
      Rectangle2D.Float drawingArea = myDrawnRectangles.get(i);
      Rectangle2D.Float clampedDrawingArea = new Rectangle2D.Float(
        Math.max(0, drawingArea.x),
        drawingArea.y,
        Math.min(drawingArea.x + drawingArea.width, dim.width - myNodeXPaddingPx) - Math.max(0, drawingArea.x),
        drawingArea.height);
      myRenderer.render(g, node, drawingArea, clampedDrawingArea, node == myFocusedNode, mySelectedNode != null && node != mySelectedNode);
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
    float left = (float)((node.getStart() - myXRange.getMin()) / myXRange.getLength());
    float right = (float)((node.getEnd() - myXRange.getMin()) / myXRange.getLength());
    Rectangle2D.Float rect = new Rectangle2D.Float();
    rect.x = left;
    rect.y = (float)((getNodeHeight() + myNodeYPaddingPx) * node.getDepth() - getYRange().getMin());
    rect.width = right - left;
    rect.height = getNodeHeight();
    return rect;
  }

  private double positionToRange(double x) {
    return x / getWidth() * myXRange.getLength() + myXRange.getMin();
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
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), ACTION_ZOOM_IN);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ACTION_ZOOM_OUT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), ACTION_ZOOM_OUT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), ACTION_MOVE_LEFT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), ACTION_MOVE_LEFT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), ACTION_MOVE_RIGHT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), ACTION_MOVE_RIGHT);

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
          eventSourceRepaint(e);
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
        double deltaY = e.getPoint().y - myLastPoint.y;
        deltaY = getOrientation() == Orientation.BOTTOM_UP ? deltaY : -deltaY;
        shiftYRange(deltaY);

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

      private void shiftYRange(double deltaY) {
        // The height of the contents we can show, including those not currently shown because of vertical scrollbar's position.
        int contentHeight = getMaximumHeight();
        // The height of the GUI component to draw the contents.
        int viewHeight = getHeight();
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
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        if (AdtUiUtils.isActionKeyDown(e)) {
          double cursorRange = positionToRange(e.getX());
          double leftDelta = (cursorRange - myXRange.getMin()) / ZOOM_FACTOR * e.getWheelRotation();
          double rightDelta = (myXRange.getMax() - cursorRange) / ZOOM_FACTOR * e.getWheelRotation();
          myXRange.set(Math.max(myGlobalXRange.getMin(), myXRange.getMin() - leftDelta),
                       Math.min(myGlobalXRange.getMax(), myXRange.getMax() + rightDelta));
        }
        else {
          double deltaY = e.getPreciseWheelRotation() * MOUSE_WHEEL_SCROLL_FACTOR;
          deltaY = getOrientation() == Orientation.TOP_DOWN ? deltaY : -deltaY;
          shiftYRange(deltaY);
        }
      }
    };
    addMouseWheelListener(adapter);
    addMouseListener(adapter);
    addMouseMotionListener(adapter);
  }

  @NotNull
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
      assert n != null;
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
    return (getNodeHeight() + myNodeYPaddingPx) * maxDepth + HEIGHT_PADDING;
  }

  private int getNodeHeight() {
    if (myCustomNodeHeightPx > 0) {
      return myCustomNodeHeightPx;
    }
    return mDefaultFontMetrics.getHeight();
  }

  public static class Builder<N extends HNode<N>> {
    @Nullable private final N myRoot;
    @NotNull private final Range myXRange;
    @NotNull private final HRenderer<N> myRenderer;

    @NotNull private Orientation myOrientation = Orientation.TOP_DOWN;
    @NotNull private Range myGlobalXRange = new Range(-Double.MAX_VALUE, Double.MAX_VALUE);
    private boolean myRootVisible = true;
    private boolean myNodeSelectionEnabled = false;
    @NotNull private HTreeChartReducer<N> myReducer = new DefaultHTreeChartReducer<>();
    private int myCustomNodeHeightPx = 0;
    private int myNodeXPaddingPx = PADDING;
    private int myNodeYPaddingPx = PADDING;

    /**
     * Creates a builder for {@link HTreeChart<N>}
     *
     * @param xRange   - the range of the chart's visible area
     * @param renderer - a {@link HRenderer<N>} which is responsible for rendering a single node.
     */
    public Builder(@Nullable N root, @NotNull Range xRange, @NotNull HRenderer<N> renderer) {
      myRoot = root;
      myXRange = xRange;
      myRenderer = renderer;
    }

    @NotNull
    public Builder<N> setOrientation(@NotNull Orientation orientation) {
      myOrientation = orientation;
      return this;
    }

    @NotNull
    public Builder<N> setRootVisible(boolean visible) {
      myRootVisible = visible;
      return this;
    }

    @NotNull
    public Builder<N> setNodeSelectionEnabled(boolean nodeSelectionEnabled) {
      myNodeSelectionEnabled = nodeSelectionEnabled;
      return this;
    }

    /**
     * @param globalXRange the bounding range of chart's visible area,
     *                     if it's not set, it assumes that there is no bounding range of chart.
     */
    @NotNull
    public Builder<N> setGlobalXRange(@NotNull Range globalXRange) {
      myGlobalXRange = globalXRange;
      return this;
    }

    @VisibleForTesting
    public Builder<N> setReducer(@NotNull HTreeChartReducer<N> reducer) {
      myReducer = reducer;
      return this;
    }

    @NotNull
    public Builder<N> setCustomNodeHeightPx(int customNodeHeightPx) {
      myCustomNodeHeightPx = customNodeHeightPx;
      return this;
    }

    @NotNull
    public Builder<N> setNodeXPaddingPx(int nodeXPaddingPx) {
      myNodeXPaddingPx = nodeXPaddingPx;
      return this;
    }

    @NotNull
    public Builder<N> setNodeYPaddingPx(int nodeYPaddingPx) {
      myNodeYPaddingPx = nodeYPaddingPx;
      return this;
    }

    @NotNull
    public HTreeChart<N> build() {
      return new HTreeChart<>(this);
    }
  }

  public enum Orientation {TOP_DOWN, BOTTOM_UP}
}