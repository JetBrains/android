/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.layout3d;

/**
 * Basic vector math
 */
public class VectorUtil {
  public static void sub(double[] a, double[] b, double[] out) {
    out[0] = a[0] - b[0];
    out[1] = a[1] - b[1];
    out[2] = a[2] - b[2];
  }

  public static void mult(double[] a, double b, double[] out) {
    out[0] = a[0] * b;
    out[1] = a[1] * b;
    out[2] = a[2] * b;
  }

  public static double dot(double[] a, double[] b) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  }

  public static double norm(double[] a) {
    return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
  }

  public static void cross(double[] a, double[] b, double[] out) {
    double out0 = a[1] * b[2] - b[1] * a[2];
    double out1 = a[2] * b[0] - b[2] * a[0];
    double out2 = a[0] * b[1] - b[0] * a[1];
    out[0] = out0;
    out[1] = out1;
    out[2] = out2;
  }

  public static void normalize(double[] a) {
    double norm = norm(a);
    a[0] /= norm;
    a[1] /= norm;
    a[2] /= norm;
  }

  public static void add(double[] a, double[] b,
                         double[] out) {
    out[0] = a[0] + b[0];
    out[1] = a[1] + b[1];
    out[2] = a[2] + b[2];
  }
}
