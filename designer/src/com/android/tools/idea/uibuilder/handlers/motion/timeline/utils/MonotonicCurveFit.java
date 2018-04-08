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

/**
 * This performs a spline interpolation in multiple dimensions
 */
public class MonotonicCurveFit extends CurveFit {
  private static final String TAG = "MonotonicCurveFit";
  private double[] mT;
  private double[][] mY;
  private double[][] mTangent;

  public MonotonicCurveFit(double[] time, double[][] y) {
    final int N = time.length;
    final int dim = y[0].length;
    double[][] slope = new double[N - 1][dim]; // could optimize this out
    double[][] tangent = new double[N][dim];
    for (int j = 0; j < dim; j++) {
      for (int i = 0; i < N - 1; i++) {
        double dt = time[i + 1] - time[i];
        slope[i][j] = (y[i + 1][j] - y[i][j]) / dt;
        if (i == 0) {
          tangent[i][j] = slope[i][j];
        }
        else {
          tangent[i][j] = (slope[i - 1][j] + slope[i][j]) * 0.5f;
        }
      }
      tangent[N - 1][j] = slope[N - 2][j];
    }

    for (int i = 0; i < N - 1; i++) {
      for (int j = 0; j < dim; j++) {
        if (slope[i][j] == 0.) {
          tangent[i][j] = 0.;
          tangent[i + 1][j] = 0.;
        }
        else {
          double a = tangent[i][j] / slope[i][j];
          double b = tangent[i + 1][j] / slope[i][j];
          double h = Math.hypot(a, b);
          if (h > 9.0) {
            double t = 3. / h;
            tangent[i][j] = t * a * slope[i][j];
            tangent[i + 1][j] = t * b * slope[i][j];
          }
        }
      }
    }
    mT = time;
    mY = y;
    mTangent = tangent;
  }

  @Override
  public void getPos(double t, double[] v) {
    final int n = mT.length;
    final int dim = mY[0].length;
    if (t <= mT[0]) {
      for (int j = 0; j < dim; j++) {
        v[j] = mY[0][j];
      }
      return;
    }
    if (t >= mT[n - 1]) {
      for (int j = 0; j < dim; j++) {
        v[j] = mY[n - 1][j];
      }
      return;
    }

    for (int i = 0; i < n - 1; i++) {
      if (t == mT[i]) {
        for (int j = 0; j < dim; j++) {
          v[j] = mY[i][j];
        }
      }
      if (t < mT[i + 1]) {
        double h = mT[i + 1] - mT[i];
        double x = (t - mT[i]) / h;
        for (int j = 0; j < dim; j++) {
          double y1 = mY[i][j];
          double y2 = mY[i + 1][j];
          double t1 = mTangent[i][j];
          double t2 = mTangent[i + 1][j];
          v[j] = interpolate(h, x, y1, y2, t1, t2);
        }
        return;
      }
    }
  }

  @Override
  public void getPos(double t, float[] v) {
    final int n = mT.length;
    final int dim = mY[0].length;
    if (t <= mT[0]) {
      for (int j = 0; j < dim; j++) {
        v[j] = (float)mY[0][j];
      }
      return;
    }
    if (t >= mT[n - 1]) {
      for (int j = 0; j < dim; j++) {
        v[j] = (float)mY[n - 1][j];
      }
      return;
    }

    for (int i = 0; i < n - 1; i++) {
      if (t == mT[i]) {
        for (int j = 0; j < dim; j++) {
          v[j] = (float)mY[i][j];
        }
      }
      if (t < mT[i + 1]) {
        double h = mT[i + 1] - mT[i];
        double x = (t - mT[i]) / h;
        for (int j = 0; j < dim; j++) {
          double y1 = mY[i][j];
          double y2 = mY[i + 1][j];
          double t1 = mTangent[i][j];
          double t2 = mTangent[i + 1][j];
          v[j] = (float)interpolate(h, x, y1, y2, t1, t2);
        }
        return;
      }
    }
  }

  @Override
  public double getPos(double t, int j) {
    final int n = mT.length;
    if (t <= mT[0]) {
      return mY[0][j];
    }
    if (t >= mT[n - 1]) {
      return mY[n - 1][j];
    }

    for (int i = 0; i < n - 1; i++) {
      if (t == mT[i]) {
        return mY[i][j];
      }
      if (t < mT[i + 1]) {
        double h = mT[i + 1] - mT[i];
        double x = (t - mT[i]) / h;
        double y1 = mY[i][j];
        double y2 = mY[i + 1][j];
        double t1 = mTangent[i][j];
        double t2 = mTangent[i + 1][j];
        return interpolate(h, x, y1, y2, t1, t2);
      }
    }
    return 0; // should never reach here
  }

  @Override
  public void getSlope(double t, double[] v) {
    final int n = mT.length;
    int dim = mY[0].length;
    if (t <= mT[0]) {
      t = mT[0];
    }
    else if (t >= mT[n - 1]) {
      t = mT[n - 1];
    }

    for (int i = 0; i < n - 1; i++) {
      if (t <= mT[i + 1]) {
        double h = mT[i + 1] - mT[i];
        double x = (t - mT[i]) / h;
        for (int j = 0; j < dim; j++) {
          double y1 = mY[i][j];
          double y2 = mY[i + 1][j];
          double t1 = mTangent[i][j];
          double t2 = mTangent[i + 1][j];
          v[j] = diff(h, x, y1, y2, t1, t2) / h;
        }
        break;
      }
    }
    return;
  }

  @Override
  public double getSlope(double t, int j) {
    final int n = mT.length;

    if (t < mT[0]) {
      t = mT[0];
    }
    else if (t >= mT[n - 1]) {
      t = mT[n - 1];
    }
    for (int i = 0; i < n - 1; i++) {
      if (t <= mT[i + 1]) {
        double h = mT[i + 1] - mT[i];
        double x = (t - mT[i]) / h;
        double y1 = mY[i][j];
        double y2 = mY[i + 1][j];
        double t1 = mTangent[i][j];
        double t2 = mTangent[i + 1][j];
        return diff(h, x, y1, y2, t1, t2) / h;
      }
    }
    return 0; // should never reach here
  }

  @Override
  public double[] getTimePoints() {
    return mT;
  }

  /**
   * Cubic Hermite spline
   *
   * @return
   */
  private static double interpolate(double h, double x, double y1, double y2, double t1, double t2) {
    double x2 = x * x;
    double x3 = x2 * x;
    return -2 * x3 * y2 + 3 * x2 * y2 + 2 * x3 * y1 - 3 * x2 * y1 + y1
           + h * t2 * x3 + h * t1 * x3 - h * t2 * x2 - 2 * h * t1 * x2
           + h * t1 * x;
  }

  /**
   * Cubic Hermite spline slope differentiated
   *
   * @return
   */
  private static double diff(double h, double x, double y1, double y2, double t1, double t2) {
    double x2 = x * x;
    return -6 * x2 * y2 + 6 * x * y2 + 6 * x2 * y1 - 6 * x * y1 + 3 * h * t2 * x2 +
           3 * h * t1 * x2 - 2 * h * t2 * x - 4 * h * t1 * x + h * t1;
  }
}