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

import java.awt.*;

/**
 * Set of insets - distances to outer left, top, right and bottom edges. These objects
 * can be used for both actual <b>margins</b> and <b>padding</b> -- and in general any
 * deltas to the bounds of a rectangle.
 */
public class Insets {
  public static final Insets NONE = new Insets(0, 0, 0, 0);
  /**
   * The left margin
   */
  @AndroidCoordinate
  public final int left;
  /**
   * The top margin
   */
  @AndroidCoordinate
  public final int top;
  /**
   * The right margin
   */
  @AndroidCoordinate
  public final int right;
  /**
   * The bottom margin
   */
  @AndroidCoordinate
  public final int bottom;

  /**
   * Creates a new {@link Insets} instance.
   *
   * @param left   the left side margin
   * @param top    the top margin
   * @param right  the right side margin
   * @param bottom the bottom margin
   */
  public Insets(@AndroidCoordinate int left,
                @AndroidCoordinate int top,
                @AndroidCoordinate int right,
                @AndroidCoordinate int bottom) {
    this.left = left;
    this.right = right;
    this.top = top;
    this.bottom = bottom;
  }

  /**
   * Returns true if this margin is empty
   *
   * @return true if empty
   */
  public boolean isEmpty() {
    return this == NONE || (left == 0 && top == 0 && right == 0 && bottom == 0);
  }

  /**
   * Applies these insets to the given bounds rectangle by subtracting them. For example, for a view that has margins,
   * you can subtract the margins to the bounds to find out the real bounds prior to the margins being applied.
   *
   * @param bounds the rectangle the be modified
   * @return true if the rectangle was modified
   */
  public boolean subtractFrom(Rectangle bounds) {
    if (isEmpty()) {
      return false;
    }

    bounds.x -= left;
    bounds.width += left;

    bounds.y -= top;
    bounds.height += top;

    bounds.width += right;
    bounds.height += bottom;
    return true;
  }

  /**
   * Applies these insets to the given bounds rectangle by adding them. For example, for a view that has padding,
   * you can add the padding to the bounds to find out the bounds of the child content.
   *
   * @param bounds the rectangle the be modified
   * @return true if the rectangle was modified
   */
  public boolean addTo(Rectangle bounds) {
    if (isEmpty()) {
      return false;
    }

    bounds.x += left;
    bounds.width -= left;

    bounds.y += top;
    bounds.height -= top;

    bounds.width -= right;
    bounds.height -= bottom;
    return true;
  }

  /**
   * Returns the total width of the left and right insets; e.g. if you have 2px on the
   * left and 3px on the right, the inset width is 5px.
   */
  @AndroidCoordinate
  public int width() {
    return left + right;
  }

  /**
   * Returns the total height of the top and bottom insets; e.g. if you have 2px on the
   * top and 3px on the bottom, the inset height is 5px.
   */
  @AndroidCoordinate
  public int height() {
    return top + bottom;
  }

  @NotNull
  @Override
  public String toString() {
    return "Margins [left=" + left + ", right=" + right + ", top=" + top + ", bottom=" + bottom + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Insets insets = (Insets)o;

    if (bottom != insets.bottom) return false;
    if (left != insets.left) return false;
    if (right != insets.right) return false;
    if (top != insets.top) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = left;
    result = 31 * result + top;
    result = 31 * result + right;
    result = 31 * result + bottom;
    return result;
  }
}
