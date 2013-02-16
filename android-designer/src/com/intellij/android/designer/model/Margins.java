/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.model;

import org.jetbrains.annotations.NotNull;

/**
 * Set of margins - distances to outer left, top, right and bottom edges. These objects
 * can be used for both actual <b>margins</b> as well as insets - and in general any
 * deltas to the bounds of a rectangle.
 */
public class Margins {
  /**
   * The left margin
   */
  public final int left;

  /**
   * The top margin
   */
  public final int top;

  /**
   * The right margin
   */
  public final int right;

  /**
   * The bottom margin
   */
  public final int bottom;

  public static final Margins NONE = new Margins(0, 0, 0, 0);

  /**
   * Creates a new {@link Margins} instance.
   *
   * @param left   the left side margin
   * @param top    the top margin
   * @param right  the right side margin
   * @param bottom the bottom margin
   */
  public Margins(int left, int top, int right, int bottom) {
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
    return left == 0 && top == 0 && right == 0 && bottom == 0;
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

    Margins margins = (Margins)o;

    if (bottom != margins.bottom) return false;
    if (left != margins.left) return false;
    if (right != margins.right) return false;
    if (top != margins.top) return false;

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
