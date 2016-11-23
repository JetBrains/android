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

public class Range  {

  protected double myCurrentMin;

  protected double myCurrentMax;

  public Range(double min, double max) {
    myCurrentMin = min;
    myCurrentMax = max;
  }

  public Range() {
    this(0, 0);
  }

  public void setMin(double from) {
    myCurrentMin = from;
  }

  public void setMax(double to) {
    myCurrentMax = to;
  }

  public void set(double min, double max) {
    setMin(min);
    setMax(max);
  }

  public void set(Range other) {
    set(other.getMin(), other.getMax());
  }

  public double getMin() {
    return myCurrentMin;
  }

  public double getMax() {
    return myCurrentMax;
  }

  public double getLength() {
    return Math.abs(getMax() - getMin());
  }

  public boolean isPoint() {
    return getMax() == getMin();
  }

  /**
   * Returns the closest value to a number that is within the Range or the number itself if it already is.
   * @param value The number to be clamped
   */
  public double clamp(double value) {
    return Math.min(Math.max(getMin(), value), getMax());
  }

  /**
   * Flips the values of min and max.
   */
  public void flip() {
    set(getMax(), getMin());
  }

  /**
   * Shifts the values of min and max by a determined delta.
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
    if (isPoint()) {
      // All gone
    } else if (range.isPoint()) {
      ret.add(this);
    } else if (range.getMin() > getMin() && range.getMax() < getMax()) {
      ret.add(new Range(getMin(), range.getMin()));
      ret.add(new Range(range.getMax(), getMax()));
    } else if (range.getMin() > getMin() && range.getMin() < getMax()) {
      ret.add(new Range(getMin(), range.getMin()));
    } else if (range.getMax() > getMin() && range.getMax() < getMax()) {
      ret.add(new Range(range.getMax(), getMax()));
    } else if (range.getMin() > getMax() || range.getMax() < getMin()){
      ret.add(this);
    }
    return ret;
  }

  /**
   * Returns the intersection range or [0,0] if there is no intersection.
   * Note that this doesn't handle correctly intersections with empty ranges.
   */
  public Range getIntersection(Range range) {
    if (range.getMin() > getMax() || range.getMax() < getMin()) {
      return new Range();
    } else {
      return new Range(Math.max(getMin(), range.getMin()), Math.min(getMax(), range.getMax()));
    }
  }
}