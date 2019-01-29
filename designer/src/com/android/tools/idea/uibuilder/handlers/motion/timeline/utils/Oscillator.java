/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.idea.uibuilder.handlers.motion.timeline.utils;

import java.util.Arrays;

/**
 * This generates variable frequency oscillation curves
 */
public class Oscillator {
  public static String TAG = "Oscillator";
  float[] mPeriod = {};
  double[] mPosition = {};
  double[] mArea;

  public static final int SIN_WAVE = 0; // theses must line up with attributes
  public static final int SQUARE_WAVE = 1;
  public static final int TRIANGLE_WAVE = 2;
  public static final int SAW_WAVE = 3;
  public static final int REVERSE_SAW_WAVE = 4;
  public static final int COS_WAVE = 5;
  public static final int BOUNCE = 6;
  int mType;
  double PI2 = Math.PI * 2;
  private boolean mNormalized = false;

  public Oscillator() {
  }

  @Override
  public String toString() {
    return "pos =" + Arrays.toString(mPosition) + " period=" + Arrays.toString(mPeriod);
  }

  public void setType(int type) {
    mType = type;
  }

  public void addPoint(double position, float period) {
    int len = mPeriod.length + 1;

    int j = Arrays.binarySearch(mPosition, position);
    if (j < 0) {
      j = -j - 1;
    }
    mPosition = Arrays.copyOf(mPosition, len);
    mPeriod = Arrays.copyOf(mPeriod, len);
    mArea = new double[len];
    System.arraycopy(mPosition, j, mPosition, j + 1, len - j - 1);
    mPosition[j] = position;
    mPeriod[j] = period;
    mNormalized = false;
  }

  /**
   * After adding point every thing must be normalized
   */
  public void normalize() {
    double totalArea = 0;
    double totalCount = 0;
    for (int i = 0; i < mPeriod.length; i++) {
      totalCount += mPeriod[i];
    }
    for (int i = 1; i < mPeriod.length; i++) {
      float h = (mPeriod[i - 1] + mPeriod[i]) / 2;
      double w = mPosition[i] - mPosition[i - 1];
      totalArea = totalArea + w * h;
    }
    // scale periods to normalize it
    for (int i = 0; i < mPeriod.length; i++) {
      mPeriod[i] *= totalCount / totalArea;
    }

    mArea[0] = 0;
    for (int i = 1; i < mPeriod.length; i++) {
      float h = (mPeriod[i - 1] + mPeriod[i]) / 2;
      double w = mPosition[i] - mPosition[i - 1];
      mArea[i] = mArea[i - 1] + w * h;
    }
    mNormalized = true;
  }

  double getP(double time) {
    if (time < 0) {
      time = 0;
    }
    else if (time > 1) {
      time = 1;
    }
    int index = Arrays.binarySearch(mPosition, time);
    double p = 0;
    if (index > 0) {
      p = 1;
    }
    else if (index != 0) {
      index = -index - 1;
      double t = time;
      double m = (mPeriod[index] - mPeriod[index - 1]) / (mPosition[index] - mPosition[index - 1]);
      p = mArea[index - 1]
          + (mPeriod[index - 1] - m * mPosition[index - 1]) * (t - mPosition[index - 1])
          + m * (t * t - mPosition[index - 1] * mPosition[index - 1]) / 2;
    }
    return p;
  }

  public double getValue(double time) {

    switch (mType) {
      default:
      case SIN_WAVE:
        return Math.sin(PI2 * getP(time));
      case SQUARE_WAVE:
        return Math.signum(0.5 - getP(time) % 1);
      case TRIANGLE_WAVE:
        return 1 - Math.abs(((getP(time)) * 4 + 1) % 4 - 2);
      case SAW_WAVE:
        return ((getP(time) * 2 + 1) % 2) - 1;
      case REVERSE_SAW_WAVE:
        return (1 - ((getP(time) * 2 + 1) % 2));
      case COS_WAVE:
        return Math.cos(PI2 * getP(time));
      case BOUNCE:
        double x = 1 - Math.abs(((getP(time)) * 4) % 4 - 2);
        return 1 - x * x;
    }
  }
}
