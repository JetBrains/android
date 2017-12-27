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
 * This defines a Quaternion with a standard set of operations typically found on one
 */
public class Quaternion {
  private final double[] x = new double[4]; // w,x,y,z,

  public void set(double w, double x, double y, double z) {
    this.x[0] = w;
    this.x[1] = x;
    this.x[2] = y;
    this.x[3] = z;
  }

  private static double[] cross(double[] a, double[] b) {
    double out0 = a[1] * b[2] - b[1] * a[2];
    double out1 = a[2] * b[0] - b[2] * a[0];
    double out2 = a[0] * b[1] - b[0] * a[1];
    return new double[]{out0, out1, out2};
  }

  private static double dot(double[] a, double[] b) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  }

  private static double[] normal(double[] a) {
    double norm = Math.sqrt(dot(a, a));
    return new double[]{a[0] / norm, a[1] / norm, a[2] / norm};
  }

  public void set(double[] v1, double[] v2) {
    double[] vec1 = normal(v1);
    double[] vec2 = normal(v2);
    double[] axis = normal(cross(vec1, vec2));
    double angle = Math.acos(dot(vec1, vec2));
    set(angle, axis);
  }

  public static double calcAngle(double[] v1, double[] v2) {
    double[] vec1 = normal(v1);
    double[] vec2 = normal(v2);
    return Math.acos(dot(vec1, vec2));
  }

  public static double[] calcAxis(double[] v1, double[] v2) {
    double[] vec1 = normal(v1);
    double[] vec2 = normal(v2);
    return normal(cross(vec1, vec2));
  }

  public void set(double angle, double[] axis) {
    x[0] = Math.cos(angle / 2);
    double sin = Math.sin(angle / 2);
    x[1] = axis[0] * sin;
    x[2] = axis[1] * sin;
    x[3] = axis[2] * sin;
  }

  public Quaternion(double x0, double x1, double x2, double x3) {
    x[0] = x0;
    x[1] = x1;
    x[2] = x2;
    x[3] = x3;
  }

  public Quaternion conjugate() {
    return new Quaternion(x[0], -x[1], -x[2], -x[3]);
  }

  public Quaternion plus(Quaternion b) {
    Quaternion a = this;
    return new Quaternion(a.x[0] + b.x[0], a.x[1] + b.x[1], a.x[2] + b.x[2], a.x[3] + b.x[3]);
  }

  public Quaternion times(Quaternion b) {
    Quaternion a = this;
    double y0 = a.x[0] * b.x[0] - a.x[1] * b.x[1] - a.x[2] * b.x[2] - a.x[3] * b.x[3];
    double y1 = a.x[0] * b.x[1] + a.x[1] * b.x[0] + a.x[2] * b.x[3] - a.x[3] * b.x[2];
    double y2 = a.x[0] * b.x[2] - a.x[1] * b.x[3] + a.x[2] * b.x[0] + a.x[3] * b.x[1];
    double y3 = a.x[0] * b.x[3] + a.x[1] * b.x[2] - a.x[2] * b.x[1] + a.x[3] * b.x[0];
    return new Quaternion(y0, y1, y2, y3);
  }

  public Quaternion inverse() {
    double d = x[0] * x[0] + x[1] * x[1] + x[2] * x[2] + x[3] * x[3];
    return new Quaternion(x[0] / d, -x[1] / d, -x[2] / d, -x[3] / d);
  }

  public Quaternion divides(Quaternion b) {
    Quaternion a = this;
    return a.inverse().times(b);
  }

  public double[] rotateVec(double[] v) {

    double v0 = v[0];
    double v1 = v[1];
    double v2 = v[2];

    double s = x[1] * v0 + x[2] * v1 + x[3] * v2;

    double n0 = 2 * (x[0] * (v0 * x[0] - (x[2] * v2 - x[3] * v1)) + s * x[1]) - v0;
    double n1 = 2 * (x[0] * (v1 * x[0] - (x[3] * v0 - x[1] * v2)) + s * x[2]) - v1;
    double n2 = 2 * (x[0] * (v2 * x[0] - (x[1] * v1 - x[2] * v0)) + s * x[3]) - v2;

    return new double[]{n0, n1, n2};
  }

  void matrix() {
    double xx = x[1] * x[1];
    double xy = x[1] * x[2];
    double xz = x[1] * x[3];
    double xw = x[1] * x[0];

    double yy = x[2] * x[2];
    double yz = x[2] * x[3];
    double yw = x[2] * x[0];

    double zz = x[3] * x[3];
    double zw = x[3] * x[0];
    double[] m = new double[16];
    m[0] = 1 - 2 * (yy + zz);
    m[1] = 2 * (xy - zw);
    m[2] = 2 * (xz + yw);

    m[4] = 2 * (xy + zw);
    m[5] = 1 - 2 * (xx + zz);
    m[6] = 2 * (yz - xw);

    m[8] = 2 * (xz - yw);
    m[9] = 2 * (yz + xw);
    m[10] = 1 - 2 * (xx + yy);

    m[3] = m[7] = m[11] = m[12] = m[13] = m[14] = 0;
    m[15] = 1;
  }
}
