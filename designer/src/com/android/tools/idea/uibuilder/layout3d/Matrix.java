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

import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * A basic 4x4 matrix class
 */
public class Matrix {

  public double[] m;

  public void makeRotation() {
    {
      double[] v = {m[0], m[4], m[8]};
      VectorUtil.normalize(v);
      m[0] = v[0];
      m[4] = v[1];
      m[8] = v[2];
    }
    {
      double[] v = {m[1], m[5], m[9]};
      VectorUtil.normalize(v);
      m[1] = v[0];
      m[5] = v[1];
      m[9] = v[2];
    }
    {
      double[] v = {m[2], m[6], m[10]};
      VectorUtil.normalize(v);
      m[2] = v[0];
      m[6] = v[1];
      m[10] = v[2];
    }
  }

  private static String trim(String s) {
    return s.substring(s.length() - 7);
  }

  public void print() {
    DecimalFormat df = new DecimalFormat("      ##0.000");
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        System.out.print(((j == 0) ? "[ " : " , ") + trim(df.format(m[i * 4 + j])));
      }
      System.out.println("]");
    }
  }

  public Matrix() {
    m = new double[4 * 4];
    setToUnit();
  }

  public Matrix(Matrix matrix) {
    this(Arrays.copyOf(matrix.m, matrix.m.length));
  }

  protected Matrix(double[] m) {
    this.m = m;
  }

  public void setToUnit() {
    for (int i = 1; i < m.length; i++) {
      m[i] = 0;
    }
    m[0] = 1;
    m[5] = 1;
    m[10] = 1;
    m[15] = 1;
  }

  public void mult4(float[] src, float[] dest) {
    for (int i = 0; i < 4; i++) {
      int col = i * 4;
      double sum = 0;
      for (int j = 0; j < 4; j++) {
        sum += m[col + j] * src[j];
      }
      dest[i] = (float)sum;
    }
  }

  public void mult3(float[] src, float[] dest) {
    for (int i = 0; i < 3; i++) {
      int col = i * 4;
      double sum = m[col + 3];
      for (int j = 0; j < 3; j++) {
        sum += m[col + j] * src[j];
      }
      dest[i] = (float)sum;
    }
  }

  public void mult3v(float[] src, float[] dest) {
    for (int i = 0; i < 3; i++) {
      int col = i * 4;
      double sum = 0;
      for (int j = 0; j < 3; j++) {
        sum += m[col + j] * src[j];
      }
      dest[i] = (float)sum;
    }
  }

  public void mult4(double[] src, double[] dest) {
    for (int i = 0; i < 4; i++) {
      int col = i * 4;
      double sum = 0;
      for (int j = 0; j < 4; j++) {
        sum += m[col + j] * src[j];
      }
      dest[i] = (float)sum;
    }
  }

  public void mult3(double[] src, double[] dest) {
    for (int i = 0; i < 3; i++) {
      int col = i * 4;
      double sum = m[col + 3];
      for (int j = 0; j < 3; j++) {
        sum += m[col + j] * src[j];
      }
      dest[i] = (float)sum;
    }
  }

  public void mult3v(double[] src, double[] dest) {
    for (int i = 0; i < 3; i++) {
      int col = i * 4;
      double sum = 0;
      for (int j = 0; j < 3; j++) {
        sum += m[col + j] * src[j];
      }
      dest[i] = (float)sum;
    }
  }

  public double[] vecmult(double[] src) {
    double[] ret = new double[3];
    mult3v(src, ret);
    return ret;
  }

  public void mult3(float[] src, int off1, float[] dest, int off2) {

    int col = 0 * 4;
    double sum = m[col + 3];
    for (int j = 0; j < 3; j++) {
      sum += m[col + j] * src[j + off1];
    }
    float v0 = (float)sum;

    col = 1 * 4;
    sum = m[col + 3];
    for (int j = 0; j < 3; j++) {
      sum += m[col + j] * src[j + off1];
    }

    float v1 = (float)sum;

    col = 2 * 4;
    sum = m[col + 3];
    for (int j = 0; j < 3; j++) {
      sum += m[col + j] * src[j + off1];
    }
    float v2 = (float)sum;

    dest[off2] = v0;
    dest[1 + off2] = v1;
    dest[2 + off2] = v2;
  }

  public Matrix invers() {
    double[] inv = new double[16];

    inv[0] = m[5] * m[10] * m[15] -
             m[5] * m[11] * m[14] -
             m[9] * m[6] * m[15] +
             m[9] * m[7] * m[14] +
             m[13] * m[6] * m[11] -
             m[13] * m[7] * m[10];

    inv[4] = -m[4] * m[10] * m[15] +
             m[4] * m[11] * m[14] +
             m[8] * m[6] * m[15] -
             m[8] * m[7] * m[14] -
             m[12] * m[6] * m[11] +
             m[12] * m[7] * m[10];

    inv[8] = m[4] * m[9] * m[15] -
             m[4] * m[11] * m[13] -
             m[8] * m[5] * m[15] +
             m[8] * m[7] * m[13] +
             m[12] * m[5] * m[11] -
             m[12] * m[7] * m[9];

    inv[12] = -m[4] * m[9] * m[14] +
              m[4] * m[10] * m[13] +
              m[8] * m[5] * m[14] -
              m[8] * m[6] * m[13] -
              m[12] * m[5] * m[10] +
              m[12] * m[6] * m[9];

    inv[1] = -m[1] * m[10] * m[15] +
             m[1] * m[11] * m[14] +
             m[9] * m[2] * m[15] -
             m[9] * m[3] * m[14] -
             m[13] * m[2] * m[11] +
             m[13] * m[3] * m[10];

    inv[5] = m[0] * m[10] * m[15] -
             m[0] * m[11] * m[14] -
             m[8] * m[2] * m[15] +
             m[8] * m[3] * m[14] +
             m[12] * m[2] * m[11] -
             m[12] * m[3] * m[10];

    inv[9] = -m[0] * m[9] * m[15] +
             m[0] * m[11] * m[13] +
             m[8] * m[1] * m[15] -
             m[8] * m[3] * m[13] -
             m[12] * m[1] * m[11] +
             m[12] * m[3] * m[9];

    inv[13] = m[0] * m[9] * m[14] -
              m[0] * m[10] * m[13] -
              m[8] * m[1] * m[14] +
              m[8] * m[2] * m[13] +
              m[12] * m[1] * m[10] -
              m[12] * m[2] * m[9];

    inv[2] = m[1] * m[6] * m[15] -
             m[1] * m[7] * m[14] -
             m[5] * m[2] * m[15] +
             m[5] * m[3] * m[14] +
             m[13] * m[2] * m[7] -
             m[13] * m[3] * m[6];

    inv[6] = -m[0] * m[6] * m[15] +
             m[0] * m[7] * m[14] +
             m[4] * m[2] * m[15] -
             m[4] * m[3] * m[14] -
             m[12] * m[2] * m[7] +
             m[12] * m[3] * m[6];

    inv[10] = m[0] * m[5] * m[15] -
              m[0] * m[7] * m[13] -
              m[4] * m[1] * m[15] +
              m[4] * m[3] * m[13] +
              m[12] * m[1] * m[7] -
              m[12] * m[3] * m[5];

    inv[14] = -m[0] * m[5] * m[14] +
              m[0] * m[6] * m[13] +
              m[4] * m[1] * m[14] -
              m[4] * m[2] * m[13] -
              m[12] * m[1] * m[6] +
              m[12] * m[2] * m[5];

    inv[3] = -m[1] * m[6] * m[11] +
             m[1] * m[7] * m[10] +
             m[5] * m[2] * m[11] -
             m[5] * m[3] * m[10] -
             m[9] * m[2] * m[7] +
             m[9] * m[3] * m[6];

    inv[7] = m[0] * m[6] * m[11] -
             m[0] * m[7] * m[10] -
             m[4] * m[2] * m[11] +
             m[4] * m[3] * m[10] +
             m[8] * m[2] * m[7] -
             m[8] * m[3] * m[6];

    inv[11] = -m[0] * m[5] * m[11] +
              m[0] * m[7] * m[9] +
              m[4] * m[1] * m[11] -
              m[4] * m[3] * m[9] -
              m[8] * m[1] * m[7] +
              m[8] * m[3] * m[5];

    inv[15] = m[0] * m[5] * m[10] -
              m[0] * m[6] * m[9] -
              m[4] * m[1] * m[10] +
              m[4] * m[2] * m[9] +
              m[8] * m[1] * m[6] -
              m[8] * m[2] * m[5];

    double det;
    det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];

    if (det == 0) {
      return null;
    }

    det = 1.0 / det;

    double[] out = new double[16];

    for (int i = 0; i < 16; i++) {
      out[i] = inv[i] * det;
    }

    Matrix ret = new Matrix(out);
    return ret;
  }

  Matrix mult(Matrix b) {
    return new Matrix(multiply(this.m, b.m));
  }

  Matrix premult(Matrix b) {
    return new Matrix(multiply(b.m, this.m));
  }

  private static double[] multiply(double a[], double b[]) {
    double[] resultant = new double[16];
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        for (int k = 0; k < 4; k++) {
          resultant[i + 4 * j] += a[i + 4 * k] * b[k + 4 * j];
        }
      }
    }

    return resultant;
  }
}
