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
package com.android.tools.adtui;

import com.android.tools.adtui.common.AdtUiCursorType;
import com.android.tools.adtui.common.AdtUiCursorsProvider;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Rectangle2D;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/**
 * A component for performing/rendering selection.
 */
public class RangeSelectionComponent extends AnimatedComponent {
  private static final Color DRAG_BAR_COLOR = new JBColor(new Color(0x260478DA, true), new Color(0x3374B7FF, true));

  private static final int DEFAULT_DRAG_BAR_HEIGHT = 26;

  private static final float HANDLE_WIDTH = 3.0f;

  // Make the handle hitbox slightly larger than it actual is to make it easier to click on.
  @VisibleForTesting
  static final float HANDLE_HITBOX_WIDTH = 10.0f;

  // Minimum distance to keep handles separate from each other. This keeps handles from overlapping when selection range is too small.
  private static final float MIN_HANDLE_DISTANCE = 2.0f;

  private static final double SELECTION_MOVE_PERCENT = 0.01;

  /**
   * The ratio of selection range to view range when making a single click.
   * It is ignored when {@link #myIsPointSelectionReplaced} is false.
   */
  public static final double CLICK_RANGE_RATIO = 0.003;

  private int myMousePressed;

  private int myMouseMovedX;

  public enum Mode {
    /**
     * The default mode: nothing is happening
     */
    NONE,
    /**
     * User is currently creating / sizing a new selection.
     */
    CREATE,
    /**
     * User is over the drag bar, or moving a selection.
     */
    MOVE,
    /**
     * User is adjusting the min.
     */
    ADJUST_MIN,
    /** User is adjusting the max. */
    ADJUST_MAX
  }

  private enum ShiftDirection {
    /** User is moving the selection to the left */
    LEFT,
    /** User is moving the selection to the right */
    RIGHT,
  }

  private Mode myMode;

  /**
   * The range being selected.
   */
  @NotNull
  private final RangeSelectionModel myModel;

  /**
   * Flag to tell the component to render the grab bar if the mouse is over the selection component.
   */
  private boolean myIsMouseOverComponent;

  /**
   * Whether point selection should be replaced by a small range.
   */
  private boolean myIsPointSelectionReplaced;

  private int myDragBarHeight = DEFAULT_DRAG_BAR_HEIGHT;

  @NotNull private BooleanSupplier myRangeOcclusionTest = () -> false;

  public RangeSelectionComponent(@NotNull RangeSelectionModel model) {
    this(model, false);
  }

  public RangeSelectionComponent(@NotNull RangeSelectionModel model, boolean isPointSelectionReplaced) {
    myModel = model;
    myMode = Mode.NONE;
    myIsPointSelectionReplaced = isPointSelectionReplaced;
    setFocusable(true);
    initListeners();

    myModel.addDependency(myAspectObserver).onChange(RangeSelectionModel.Aspect.SELECTION, this::opaqueRepaint);
    myModel.getViewRange().addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::opaqueRepaint);
  }

  /**
   * @param rangeOcclusionTest A test of the current state whether the mouse is on top of an item occluding the range
   */
  public void setRangeOcclusionTest(@NotNull BooleanSupplier rangeOcclusionTest) {
    myRangeOcclusionTest = rangeOcclusionTest;
  }

  protected void initListeners() {
    this.addHierarchyListener(new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if (!RangeSelectionComponent.this.isDisplayable() || !RangeSelectionComponent.this.isShowing()) {
          resetMouse();
        }
      }
    });
    this.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          // Always clear selection on double click.
          if (e.getClickCount() == 2 && !e.isConsumed()) {
            myModel.clear();
            return;
          }
          requestFocusInWindow();
          myMode = getModeAtCurrentPosition(e.getX(), e.getY());
          if (myMode == Mode.CREATE) {
            double value = xToRange(e.getX());
            myModel.beginUpdate();
            // We clear the selection model explicitly, to make sure the "set" call fires a
            // selection creation event (instead of the model thinking we're modifying an existing
            // selection).
            myModel.clear();
            myModel.set(value, value);
          }
          myMousePressed = e.getX();
          updateCursor(myMode, myMousePressed);
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          if (myIsPointSelectionReplaced && myModel.getSelectionRange().getLength() == 0) {
            Range range = myModel.getSelectionRange();
            double delta = myModel.getViewRange().getLength() * CLICK_RANGE_RATIO;
            myModel.set(range.getMin() - delta, range.getMax() + delta);
          }

          if (myMode == Mode.CREATE) {
            myModel.endUpdate();
          }
          myMode = getModeAtCurrentPosition(e.getX(), e.getY());
          myMousePressed = -1;
          updateCursor(myMode, myMousePressed);
          opaqueRepaint();
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        resetMouse();
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        myIsMouseOverComponent = true;
      }
    });
    this.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        double pressed = xToRange(myMousePressed);
        double current = xToRange(e.getX());
        double rangeDelta = current - pressed;
        double min = myModel.getSelectionRange().getMin();
        double max = myModel.getSelectionRange().getMax();
        myMouseMovedX = e.getX();
        switch (myMode) {
          case ADJUST_MIN:
            if (min + rangeDelta > max) {
              myModel.set(max, min + rangeDelta);
              myMode = Mode.ADJUST_MAX;
            }
            else {
              myModel.set(min + rangeDelta, max);
            }
            myMousePressed = e.getX();
            break;
          case ADJUST_MAX:
            if (max + rangeDelta < min) {
              myModel.set(max + rangeDelta, min);
              myMode = Mode.ADJUST_MIN;
            }
            else {
              myModel.set(min, max + rangeDelta);
            }
            myMousePressed = e.getX();
            break;
          case MOVE:
            myModel.set(min + rangeDelta, max + rangeDelta);
            myMousePressed = e.getX();
            break;
          case CREATE:
            myModel.set(pressed < current ? pressed : current,
                        pressed < current ? current : pressed);
            break;
          case NONE:
            break;
        }
        updateCursor(myMode, e.getX());
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        myMode = getModeAtCurrentPosition(e.getX(), e.getY());
        updateCursor(myMode, e.getX());
        myMouseMovedX = e.getX();
        // Need to force a repaint when mouse is moving over the selection component to have the grab bar paint properly. If we don't
        // do this the grab bar will only refresh under the tooltip and not along the entire capture area.
        opaqueRepaint();
      }
    });
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_ESCAPE:
            if (!myModel.getSelectionRange().isEmpty()) {
              myModel.getSelectionRange().clear();
              e.consume();
            }
            break;
          case KeyEvent.VK_LEFT:
            // If we are shifting left with the alt key down, we are shrinking our selection from the right.
            // If we are shifting left with the shift key down, we are growing our selection to the left.
            shiftModel(ShiftDirection.LEFT, e.isAltDown(), e.isShiftDown());
            break;
          case KeyEvent.VK_RIGHT:
            // If we are shifting right with the shift key down, we are growing our selection to the right.
            // If we are shifting right with the alt key down, we are shrinking our selection from the left
            shiftModel(ShiftDirection.RIGHT, e.isShiftDown(), e.isAltDown());
            break;
        }
        myModel.endUpdate();
      }
    });
  }

  private void resetMouse() {
    setCursor(Cursor.getDefaultCursor());
    myIsMouseOverComponent = false;
  }

  private void shiftModel(ShiftDirection direction, boolean zeroMin, boolean zeroMax) {
    double min = myModel.getSelectionRange().getMin();
    double max = myModel.getSelectionRange().getMax();
    double rangeDelta = myModel.getViewRange().getLength() * SELECTION_MOVE_PERCENT;
    rangeDelta = (direction == ShiftDirection.LEFT) ? rangeDelta * -1 : rangeDelta;
    double minDelta = zeroMin ? 0 : rangeDelta;
    double maxDelta = zeroMax ? 0 : rangeDelta;
    // If we don't have a selection attempt to put the selection in the center off the screen.
    if (max < min) {
      max = min = myModel.getViewRange().getLength() / 2.0 + myModel.getViewRange().getMin();
    }

    myModel.beginUpdate();
    myModel.set(min + minDelta, max + maxDelta);
    myModel.endUpdate();
  }

  protected double xToRange(int x) {
    return x / getSize().getWidth() * getModel().getViewRange().getLength() + getModel().getViewRange().getMin();
  }

  protected float rangeToX(double value, double width) {
    // Clamp the range to the edge of the screen. This prevents fill artifacts when zoomed in, and improves performance.
    // If we do not clamp the selection to the screen then during painting java attempts to fill a rectangle several
    // thousand pixels off screen in both directions. This results in lots of computation that isn't required as well as,
    // lots of artifacts in the selection itself.
    return (float)Math.min(
      Math.max(
        width * (value - getModel().getViewRange().getMin()) / (getModel().getViewRange().getMax() - getModel().getViewRange().getMin()),
        0),
      width);
  }

  /**
   * l                r
   * ++|+|<--Drag Bar-->|+|++
   * ++|+|              |+|++
   * ++|+|              |+|++
   * s                  e
   * <p>
   * l: left handle
   * r: right handle
   * s: selection start
   * e: selection end
   * +: handle hit box
   */
  private Mode getModeAtCurrentPosition(int x, int y) {
    if (myModel.getSelectionRange().isEmpty()) {
      return Mode.CREATE;
    }

    double startXPos = rangeToX(myModel.getSelectionRange().getMin(), getWidth());
    double endXPos = rangeToX(myModel.getSelectionRange().getMax(), getWidth());
    if (startXPos - HANDLE_HITBOX_WIDTH < x && x < startXPos + HANDLE_WIDTH) {
      return Mode.ADJUST_MIN;
    }
    else if (endXPos - HANDLE_WIDTH < x && x < endXPos + HANDLE_HITBOX_WIDTH) {
      return Mode.ADJUST_MAX;
    }
    else if (startXPos + HANDLE_WIDTH <= x && x <= endXPos - HANDLE_WIDTH && y <= myDragBarHeight) {
      return Mode.MOVE;
    }
    return Mode.CREATE;
  }

  private void updateCursor(Mode newMode, int newX) {
    if (myRangeOcclusionTest.getAsBoolean()) {
      setCursor(Cursor.getDefaultCursor());
      return;
    }
    switch (newMode) {
      case ADJUST_MIN:
        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        break;
      case ADJUST_MAX:
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
        break;
      case MOVE:
        setCursor(AdtUiCursorsProvider.getInstance().getCursor((myMousePressed == -1) ? AdtUiCursorType.GRAB : AdtUiCursorType.GRABBING));
        break;
      case CREATE:
        double mouseRange = xToRange(newX);
        if (myMode == Mode.CREATE && myModel.canSelectRange(new Range(mouseRange, mouseRange))) {
          // If already in CREATE mode, update cursor in case selection changed direction, e.g.
          // dragging max handle below min handle.
          if (myMousePressed < newX) {
            setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
          }
          else {
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
          }
        }
        else {
          setCursor(Cursor.getDefaultCursor());
        }
        break;
      case NONE:
        // NO-OP: Keep current mouse cursor.
        break;
    }
  }

  /**
   * This listens on the selection range finishes update changes. Because the selection model fires the selection aspect
   * when the mouse is not released, cannot listen on the selection model side.
   */
  public void addSelectionUpdatedListener(final Consumer<Range> listener) {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          listener.accept(myModel.getSelectionRange());
        }
      }
    });
  }

  @NotNull
  public Mode getMode() {
    return myMode;
  }

  @NotNull
  public RangeSelectionModel getModel() {
    return myModel;
  }

  /**
   * @return true if the blue seek component from {@link RangeTooltipComponent} should be visible.
   * @see {@link RangeTooltipComponent#myShowSeekComponent}
   */
  public boolean shouldShowSeekComponent() {
    return myMode != Mode.MOVE && myMode != Mode.ADJUST_MIN && myMode != Mode.ADJUST_MAX;
  }

  /**
   * @param dragBarHeight height of the bar for drag-to-move in pixels.
   */
  public void setDragBarHeight(int dragBarHeight) {
    myDragBarHeight = dragBarHeight;
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    // Draws if the selection range is fully visible or partially visible; and hide if it is empty or not visible.
    Range selectionRange = myModel.getSelectionRange();
    if (selectionRange.isEmpty() ||
        selectionRange.getMin() > myModel.getViewRange().getMax() ||
        selectionRange.getMax() < myModel.getViewRange().getMin()) {
      return;
    }
    float startXPos = rangeToX(selectionRange.getMin(), dim.getWidth());
    float endXPos = rangeToX(selectionRange.getMax(), dim.getWidth());
    float handleDistance = endXPos - startXPos - HANDLE_WIDTH * 2;
    if (handleDistance < MIN_HANDLE_DISTANCE) {
      // When handles are too close to each other, keep a minimum distance and adjust handle position from the mid-point.
      // |h|<-min->|h|
      // s           e
      //
      // s: start
      // e: end
      // h: handle length
      // min: min distance between handles
      handleDistance = MIN_HANDLE_DISTANCE;
      startXPos = (startXPos + endXPos) / 2 - HANDLE_WIDTH - MIN_HANDLE_DISTANCE / 2;
      endXPos = startXPos + HANDLE_WIDTH * 2 + MIN_HANDLE_DISTANCE;
    }

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Draw selection area.
    g.setColor(StudioColorsKt.getSelectionOverlayBackground());
    Rectangle2D.Float rect = new Rectangle2D.Float(startXPos + HANDLE_WIDTH, 0, handleDistance, dim.height);
    g.fill(rect);

    if (myMouseMovedX > startXPos && myMouseMovedX < endXPos && myIsMouseOverComponent) {
      g.setColor(DRAG_BAR_COLOR);
      g.fill(new Rectangle2D.Float(startXPos + HANDLE_WIDTH, 0, handleDistance, myDragBarHeight));
    }

    // Draw handles.
    g.setColor(StudioColorsKt.getSelectionBackground());
    g.fill(new Rectangle2D.Float(startXPos, 0, HANDLE_WIDTH, dim.height));
    g.fill(new Rectangle2D.Float(endXPos - HANDLE_WIDTH, 0, HANDLE_WIDTH, dim.height));
  }
}