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

public class Range extends AspectModel<Range.Aspect> {

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
    if (min != myMin || max != myMax) {
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
    return getMax() == getMin();
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
   * Sets the range min and max to the new values if they are, respectively, less or greater than the current value.
   */
  public void expand(double min, double max) {
    set(Math.min(min, myMin), Math.max(max, myMax));
  }

  @Override
  public String toString() {
    return !isEmpty() ? String.format("[%s..%s]", myMin, myMax) : "[]";
  }
}