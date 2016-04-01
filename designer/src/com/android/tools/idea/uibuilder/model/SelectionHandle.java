/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.intellij.lang.annotations.MagicConstant;

import java.awt.*;

/**
 * A selection handle is a small rectangle on the border of a selected view which lets you
 * change the size of the view by dragging it.
 */
public class SelectionHandle {
  /**
   * Size of the selection handle radius, in control coordinates. Note that this isn't
   * necessarily a <b>circular</b> radius; in the case of a rectangular handle, the
   * w and the h are both equal to this radius.
   * Note also that this radius is in <b>control</b> coordinates, whereas the rest
   * of the class operates in layout coordinates. This is because we do not want the
   * selection handles to grow or shrink along with the screen zoom; they are always
   * at the given pixel size in the control.
   */
  public final static int PIXEL_RADIUS = 8;

  /**
   * Extra number of pixels to look beyond the actual radius of the selection handle
   * when matching mouse positions to handles
   */
  public final static int PIXEL_MARGIN = 2;

  /**
   * The position of the handle in the selection rectangle
   */
  public enum Position {
    TOP_MIDDLE(Cursor.N_RESIZE_CURSOR, 0.5, 0),
    TOP_RIGHT(Cursor.NE_RESIZE_CURSOR, 1, 0),
    RIGHT_MIDDLE(Cursor.E_RESIZE_CURSOR, 1, 0.5),
    BOTTOM_RIGHT(Cursor.SE_RESIZE_CURSOR, 1, 1),
    BOTTOM_MIDDLE(Cursor.S_RESIZE_CURSOR, 0.5, 1),
    BOTTOM_LEFT(Cursor.SW_RESIZE_CURSOR, 0, 1),
    LEFT_MIDDLE(Cursor.W_RESIZE_CURSOR, 0, 0.5),
    TOP_LEFT(Cursor.NW_RESIZE_CURSOR, 0, 0);

    /** Factor to multiply component width to obtain handle x location */
    public final double alignX;

    /** Factor to multiply component height with to obtain handle y location */
    public final double alignY;

    /** Predefined AWT cursor constant */
    private final int myAwtCursor;

    Position(@MagicConstant(valuesFromClass = Cursor.class) int awtCursor, double alignX, double alignY) {
      myAwtCursor = awtCursor;
      this.alignX = alignX;
      this.alignY = alignY;
    }

    @MagicConstant(valuesFromClass = Cursor.class)
    private int getCursorType() {
      return myAwtCursor;
    }

    /**
     * Is the {@link SelectionHandle} somewhere on the left edge?
     */
    public boolean isLeft() {
      return this == TOP_LEFT || this == LEFT_MIDDLE || this == BOTTOM_LEFT;
    }

    /**
     * Is the {@link SelectionHandle} somewhere on the right edge?
     */
    public boolean isRight() {
      return this == TOP_RIGHT || this == RIGHT_MIDDLE || this == BOTTOM_RIGHT;
    }

    /**
     * Is the {@link SelectionHandle} somewhere on the top edge?
     */
    public boolean isTop() {
      return this == TOP_LEFT || this == TOP_MIDDLE || this == TOP_RIGHT;
    }

    /**
     * Is the {@link SelectionHandle} somewhere on the bottom edge?
     */
    public boolean isBottom() {
      return this == BOTTOM_LEFT || this == BOTTOM_MIDDLE || this == BOTTOM_RIGHT;
    }
  }

  /** The associated component */
  @NotNull
  public final NlComponent component;

  /**
   * The x coordinate of the center of the selection handle
   */
  @AndroidCoordinate
  public int getCenterX() {
    return component.x + (int)(myPosition.alignX * component.w);
  }

  /**
   * The y coordinate of the center of the selection handle
   */
  @AndroidCoordinate
  public int getCenterY() {
    return component.y + (int)(myPosition.alignY * component.h);
  }

  /**
   * The position of the handle in the selection rectangle
   */
  private final Position myPosition;

  /**
   * Constructs a new {@link SelectionHandle} at the given layout coordinate
   * corresponding to a handle at the given {@link Position}.
   *
   * @param component the associated component
   * @param position  the position of the handle in the selection rectangle
   */
  public SelectionHandle(@NotNull NlComponent component,
                         @NotNull Position position) {
    this.component = component;
    myPosition = position;
  }

  /**
   * Determines whether the given point is within the given distance in
   * Android coordinates. The distance should incorporate at least the equivalent
   * distance to the control coordinate space {@link #PIXEL_RADIUS}, but usually with a
   * few extra pixels added in to make the corners easier to target.
   *
   * @param x        the mouse x position in Android coordinates
   * @param y        the mouse y position in Android coordinates
   * @param distance the distance from the center of the handle to check whether the
   *                 point fits within
   * @return true if the given point is within the given distance of this handle
   */
  public boolean contains(@AndroidCoordinate int x, @AndroidCoordinate int y, @AndroidCoordinate int distance) {
    int xDelta = Math.abs(x - getCenterX());
    if (xDelta > distance) {
      return false;
    }
    int yDelta = Math.abs(y - getCenterY());
    return yDelta <= distance;
  }

  /**
   * Returns the position of the handle in the selection rectangle
   *
   * @return the position of the handle in the selection rectangle
   */
  public Position getPosition() {
    return myPosition;
  }

  /**
   * Returns the AWT cursor type to use for this selection handle
   *
   * @return the position of the handle in the selection rectangle
   */
  @MagicConstant(valuesFromClass = Cursor.class)
  public int getAwtCursorType() {
    return myPosition.getCursorType();
  }

  /**
   * Returns the cursor to use for this selection handle
   *
   * @return the cursor to use for this selection handle
   */
  @NotNull
  public Cursor getCursor() {
    return Cursor.getPredefinedCursor(getAwtCursorType());
  }

  /** Returns the horizontal edge (top or bottom) this selection handle is associated with, if any */
  @Nullable
  public SegmentType getHorizontalEdge() {
    return myPosition.isTop() ? SegmentType.TOP : myPosition.isBottom() ? SegmentType.BOTTOM : null;
  }

  /** Returns the vertical edge (left or right) this selection handle is associated with, if any */
  @Nullable
  public SegmentType getVerticalEdge() {
    return myPosition.isLeft() ? SegmentType.LEFT : myPosition.isRight() ? SegmentType.RIGHT : null;
  }
}