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

package com.android.tools.adtui.model;

import java.util.LinkedList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class Range extends AspectModel<Range.Aspect> {

  public enum Aspect {
    RANGE
  }

  private double myMin;

  private double myMax;

  public Range(double min, double max) {
    myMin = min;
    myMax = max;
  }

  public Range(Range other) {
    myMin = other.myMin;
    myMax = other.myMax;
  }

  public Range() {
    clear();
  }

  public void setMin(double min) {
    set(min, myMax);
  }

  public void setMax(double max) {
    set(myMin, max);
  }

  public void set(double min, double max) {
    // We use exact comparison to avoid firing when a range is set to itself.
    if (Double.compare(myMin, min) != 0 || Double.compare(myMax, max) != 0) {
      myMin = min;
      myMax = max;
      changed(Aspect.RANGE);
    }
  }

  public void set(Range other) {
    set(other.getMin(), other.getMax());
  }

  public double getMin() {
    return myMin;
  }

  public double getMax() {
    return myMax;
  }

  public double getLength() {
    return Math.abs(getMax() - getMin());
  }

  public boolean isPoint() {
    return Double.compare(getMin(), getMax()) == 0;
  }

  /**
   * Whether the range is empty.
   * A range is considered empty if its min value is greater than its max value.
   */
  public boolean isEmpty() {
    return myMin > myMax;
  }

  /**
   * Whether a value is between the range min and max (inclusive)
   */
  public boolean contains(double value) {
    return myMin <= value && value <= myMax;
  }

  /**
   * Empties a range.
   */
  public void clear() {
    // Any value of max < min would do, but using MAX_VALUE preserves the invariant:
    // For any x, x < myMax and x > myMin are false.
    set(Double.MAX_VALUE, -Double.MAX_VALUE);
  }

  /**
   * Returns the closest value to a number that is within the Range or the number itself if it already is.
   *
   * @param value The number to be clamped
   */
  public double clamp(double value) {
    return Math.min(Math.max(getMin(), value), getMax());
  }

  /**
   * Shifts the values of min and max by a determined delta.
   *
   * @param delta Number to be added to min and max
   */
  public void shift(double delta) {
    set(getMin() + delta, getMax() + delta);
  }

  /**
   * Subtracts the given range from the current range and returns the list of
   * remaining ranges. If the given range is contained within this range, it will create
   * a whole giving two resulting ranges.
   */
  public List<Range> subtract(Range range) {
    List<Range> ret = new LinkedList<>();
    Range intersection = getIntersection(range);
    if (intersection.isEmpty()) {
      ret.add(this);
    }
    else {
      Range left = new Range(myMin, intersection.getMin());
      Range right = new Range(intersection.getMax(), myMax);
      if (!left.isPoint()) {
        ret.add(left);
      }
      if (!right.isPoint()) {
        ret.add(right);
      }
    }
    return ret;
  }

  /**
   * Returns the intersection range or [0,0] if there is no intersection.
   * Note that this doesn't handle correctly intersections with empty ranges.
   *
   * This method creates a new Range object and therefore has some performance overhead. For simply testing intersection use
   * {@link #intersectsWith(Range)}. For computing the intersection length use {@link #getIntersectionLength(Range)}.
   */
  public Range getIntersection(Range range) {
    if (isEmpty() || range.isEmpty() || range.getMin() > getMax() || range.getMax() < getMin()) {
      return new Range();
    }
    else {
      return new Range(Math.max(getMin(), range.getMin()), Math.min(getMax(), range.getMax()));
    }
  }

  /**
   * @return true if this range intersects with the given range.
   */
  public boolean intersectsWith(@NotNull Range range) {
    return intersectsWith(range.getMin(), range.getMax());
  }

  /**
   * @return true if this range intersects with [min, max].
   */
  public boolean intersectsWith(double min, double max) {
    // We cannot use getIntersectionLength > 0 because the intersection may just be a point (e.g. [x, x]) and length is still 0.
    return Math.max(getMin(), min) <= Math.min(getMax(), max);
  }

  /**
   * @return length of the intersection between this range and the given range. If they don't intersect, returns 0.0.
   */
  public double getIntersectionLength(@NotNull Range range) {
    return getIntersectionLength(range.getMin(), range.getMax());
  }

  /**
   * @return length of the intersection between this range and [min, max]. If they don't intersect, returns 0.0.
   */
  public double getIntersectionLength(double min, double max) {
    double intersectionMin = Math.max(myMin, min);
    double intersectionMax = Math.min(myMax, max);
    return Math.max(0.0, intersectionMax - intersectionMin);
  }

  /**
   * Sets the range min and max to the new values if they are, respectively, less or greater than the current value.
   */
  public void expand(double min, double max) {
    set(Math.min(min, myMin), Math.max(max, myMax));
  }

  /**
   * Shifts the range to contain the bounds, only expanding if necessary
   */
  public void adjustToContain(double min, double max) {
    if (min < myMin) {
      set(min, Math.max(max, min + getLength()));
    } else if (max > myMax) {
      set(Math.min(min, max - getLength()), max);
    }
  }

  @Override
  public String toString() {
    return !isEmpty() ? String.format("[%s..%s]", myMin, myMax) : "[]";
  }

  // TODO(b/79753868): Most users would expect "equals" to work instead of requiring another method
  public boolean isSameAs(@NotNull Range otherRange) {
    return isEmpty() && otherRange.isEmpty() ||
           (Double.compare(otherRange.myMin, myMin) == 0 && Double.compare(otherRange.myMax, myMax) == 0);
  }
}