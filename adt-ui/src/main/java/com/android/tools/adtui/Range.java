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

public class Range implements Animatable {

  private static final float DEFAULT_INTERPOLATION_FRACTION = 0.95f;

  private boolean mLock;

  private double mTargetMin;

  private double mTargetMax;

  private double mCurrentMin;

  private double mCurrentMax;

  private float mFraction;

  public Range(double min, double max) {
    // TODO: Right now there's no guarantee that max is greater than, or equal to min. The flip() method even swap these values.
    // That doesn't sound right, so we should come up with a way to either use a better naming than min/max or make sure that
    // the invariant min <= max is always kept.
    mCurrentMin = mTargetMin = min;
    mCurrentMax = mTargetMax = max;
    mFraction = DEFAULT_INTERPOLATION_FRACTION;
  }

  public Range() {
    this(0, 0);
  }

  /**
   * Sets the fraction at which the min/max interpolates if the values set are not immediately
   * applied. See {@link Choreographer#lerp(double, double, float, float)} for details.
   */
  public void setInterpolationFraction(float fraction) {
    mFraction = fraction;
  }

  /**
   * @param from The min value to set to.
   * @return true if the min was set successfully, false if otherwise (e.g. the value has been locked).
   */
  public boolean setMin(double from) {
    if (mLock) {
      return false;
    }

    mTargetMin = mCurrentMin = from;
    return true;
  }

  /**
   * @param fromTarget The target min value to interpolate to.
   * @return true if the target min was set successfully, false if otherwise (e.g. the value has been locked).
   */
  public boolean setMinTarget(double fromTarget) {
    if (mLock) {
      return false;
    }

    mTargetMin = fromTarget;
    return true;
  }

  /**
   * @param to The max value to set to.
   * @return true if the max was set successfully, false if otherwise (e.g. the value has been locked).
   */
  public boolean setMax(double to) {
    if (mLock) {
      return false;
    }

    mTargetMax = mCurrentMax = to;
    return true;
  }

  /**
   * @param to The max value to interpolate to.
   * @return true if the target max was set successfully, false if otherwise (e.g. the value has been locked).
   */
  public boolean setMaxTarget(double to) {
    if (mLock) {
      return false;
    }

    mTargetMax = to;
    return true;
  }

  /**
   * Sets new values for both min and max.
   * @param min New min value
   * @param max New max value
   * @return true if max and min were set successfully, false otherwise (e.g. the values have been locked).
   */
  public boolean set(double min, double max) {
    if (mLock) {
      return false;
    }

    setMin(min);
    setMax(max);
    return true;
  }

  /**
   * Sets new values for both targetMin and targetMax.
   * @param targetMin New targetMin value
   * @param targetMax New targetMax value
   * @return true if targetMax and targetMin were set successfully, false otherwise (e.g. the values have been locked).
   */
  public boolean setTarget(double targetMin, double targetMax) {
    if (mLock) {
      return false;
    }

    setMinTarget(targetMin);
    setMaxTarget(targetMax);
    return true;
  }

  @Override
  public void animate(float frameLength) {
    if (mCurrentMin != mTargetMin) {
      mCurrentMin = Choreographer.lerp(mCurrentMin, mTargetMin, mFraction, frameLength);
    }

    if (mCurrentMax != mTargetMax) {
      mCurrentMax = Choreographer.lerp(mCurrentMax, mTargetMax, mFraction, frameLength);
    }
  }

  @Override
  public void postAnimate() {
    mLock = false;
  }

  /**
   * When called, prevents the min/max values from being set until the next animation cycle.
   */
  public void lockValues() {
    mLock = true;
  }

  public double getTargetLength() {
    return mTargetMax - mTargetMin;
  }

  /**
   * @return The current interpolated min.
   */
  public double getMin() {
    return mCurrentMin;
  }

  /**
   * @return The current interpolated max.
   */
  public double getMax() {
    return mCurrentMax;
  }

  public double getLength() {
    return Math.abs(mCurrentMax - mCurrentMin);
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
   * @return true if min and max were set successfully, false otherwise (e.g. the values have been locked).
   */
  public boolean flip() {
    return set(getMax(), getMin());
  }

  /**
   * Shifts the values of min and max by a determined delta.
   * @param delta Number to be added to min and max
   * @return true if min and max were set successfully, false otherwise (e.g. the values have been locked).
   */
  public boolean shift(double delta) {
    return set(getMin() + delta, getMax() + delta);
  }
}