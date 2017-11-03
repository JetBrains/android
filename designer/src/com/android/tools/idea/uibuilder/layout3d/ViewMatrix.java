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

import java.awt.*;
import java.text.DecimalFormat;
import java.util.Arrays;

public class ViewMatrix extends Matrix {

  double[] myLookPoint;
  double[] myEyePoint;
  double[] myUpVector;
  double myScreenWidth;
  int[] myScreenDim;

  public final static char UP_AT = 0x001;
  public final static char DOWN_AT = 0x002;
  public final static char RIGHT_AT = 0x010;
  public final static char LEFT_AT = 0x020;
  public final static char FORWARD_AT = 0x100;
  public final static char BEHIND_AT = 0x200;

  private static String toStr(double d) {
    String s = "       " + df.format(d);
    return s.substring(s.length() - 8);
  }

  private static String toStr(double[] d) {
    String s = "[";
    for (int i = 0; i < d.length; i++) {
      s += toStr(d[i]);
    }

    return s + "]";
  }

  private static DecimalFormat df = new DecimalFormat("##0.000");

  @Override
  public void print() {
    System.out.println("myLookPoint  :" + toStr(myLookPoint));
    System.out.println("myEyePoint   :" + toStr(myEyePoint));
    System.out.println("myUpVector   :" + toStr(myUpVector));
    System.out.println("myScreenWidth:" + toStr(myScreenWidth));
    System.out.println("myScreenDim  :[" + myScreenDim[0] + "," + myScreenDim[1] + "]");
  }

  public ViewMatrix() {

  }

  public void setScreenDim(int x, int y) {
    myScreenDim = new int[]{x, y};
  }

  public double[] getLookPoint() {
    return myLookPoint;
  }

  public void setLookPoint(double[] mLookPoint) {
    this.myLookPoint = mLookPoint;
  }

  public double[] getEyePoint() {
    return myEyePoint;
  }

  public void setEyePoint(double[] mEyePoint) {
    this.myEyePoint = mEyePoint;
  }

  public double[] getUpVector() {
    return myUpVector;
  }

  public void setUpVector(double[] mUpVector) {
    this.myUpVector = mUpVector;
  }

  public double getScreenWidth() {
    return myScreenWidth;
  }

  public void setScreenWidth(double screenWidth) {
    this.myScreenWidth = screenWidth;
  }

  public void makeUnit() {

  }

  public double screenDistance() {
    double[] zv =
      {myLookPoint[0] - myEyePoint[0], myLookPoint[1] - myEyePoint[1], myLookPoint[2] - myEyePoint[2]};
    return VectorUtil.norm(zv);
  }

  public void calcMatrix() {
    if (myScreenDim == null) {
      return;
    }
    double scale = myScreenWidth / myScreenDim[0];
    double[] zv = {
      myLookPoint[0] - myEyePoint[0],
      myLookPoint[1] - myEyePoint[1],
      myLookPoint[2] - myEyePoint[2]
    };
    VectorUtil.normalize(zv);

    double[] m = new double[16];
    m[2] = zv[0] * scale;
    m[6] = zv[1] * scale;
    m[10] = zv[2] * scale;
    m[14] = 0;

    calcRight(zv, myUpVector, zv);
    double[] right = zv;

    m[0] = right[0] * scale;
    m[4] = right[1] * scale;
    m[8] = right[2] * scale;
    m[12] = 0;

    m[1] = -myUpVector[0] * scale;
    m[5] = -myUpVector[1] * scale;
    m[9] = -myUpVector[2] * scale;

    m[13] = 0;
    double sw = myScreenDim[0] / 2 - 0.5;
    double sh = myScreenDim[1] / 2 - 0.5;
    double sz = -0.5;
    m[3] = myEyePoint[0] - (m[0] * sw + m[1] * sh + m[2] * sz);
    m[7] = myEyePoint[1] - (m[4] * sw + m[5] * sh + m[6] * sz);
    m[11] = myEyePoint[2] - (m[8] * sw + m[9] * sh + m[10] * sz);

    m[15] = 1;
    this.m = m;
  }

  static void calcRight(double[] a, double[] b, double[] out) {
    VectorUtil.cross(a, b, out);
  }

  public static void main(String[] args) {
    double[] up = {0, 0, 1};
    double[] look = {0, 0, 0};
    double[] eye = {-10, 0, 0};
    ViewMatrix v = new ViewMatrix();
    v.setEyePoint(eye);
    v.setLookPoint(look);
    v.setUpVector(up);
    v.setScreenWidth(10);
    v.setScreenDim(512, 512);
    v.calcMatrix();
  }

  private void calcLook(TriData tri, float[] voxelDim, int w, int h) {
    float minx = Float.MAX_VALUE, miny = Float.MAX_VALUE, minz = Float.MAX_VALUE;
    float maxx = -Float.MAX_VALUE, maxy = -Float.MAX_VALUE, maxz = -Float.MAX_VALUE;

    for (int i = 0; i < tri.myVert.length; i += 3) {
      maxx = Math.max(tri.myVert[i], maxx);
      minx = Math.min(tri.myVert[i], minx);
      maxy = Math.max(tri.myVert[i + 1], maxy);
      miny = Math.min(tri.myVert[i + 1], miny);
      maxz = Math.max(tri.myVert[i + 2], maxz);
      minz = Math.min(tri.myVert[i + 2], minz);
    }
    myLookPoint = new double[]{voxelDim[0] * (maxx + minx) / 2, voxelDim[1] * (maxy + miny) / 2,
      voxelDim[2] * (maxz + minz) / 2};

    myScreenWidth = Math.max(voxelDim[0] * (maxx - minx),
                             Math.max(voxelDim[1] * (maxy - miny), voxelDim[2] * (maxz - minz))) * 2;
  }

  private void calcLook(TriData triW) {
    float minx = Float.MAX_VALUE, miny = Float.MAX_VALUE, minz = Float.MAX_VALUE;
    float maxx = -Float.MAX_VALUE, maxy = -Float.MAX_VALUE, maxz = -Float.MAX_VALUE;

    for (int i = 0; i < triW.myVert.length; i += 3) {
      maxx = Math.max(triW.myVert[i], maxx);
      minx = Math.min(triW.myVert[i], minx);
      maxy = Math.max(triW.myVert[i + 1], maxy);
      miny = Math.min(triW.myVert[i + 1], miny);
      maxz = Math.max(triW.myVert[i + 2], maxz);
      minz = Math.min(triW.myVert[i + 2], minz);
    }
    myLookPoint = new double[]{(maxx + minx) / 2, (maxy + miny) / 2, (maxz + minz) / 2};
    myScreenWidth = Math.max((maxx - minx), Math.max((maxy - miny), (maxz - minz)));
  }

  public void look(char dir, TriData tri, int width, int height) {
    calcLook(tri);
    int dx = ((dir >> 4) & 0xF);
    int dy = ((dir >> 8) & 0xF);
    int dz = ((dir >> 0) & 0xF);
    if (dx > 1) {
      dx = -1;
    }
    if (dy > 1) {
      dy = -1;
    }
    if (dz > 1) {
      dz = -1;
    }
    myEyePoint = new double[]{myLookPoint[0] + 2 * myScreenWidth * dx,
      myLookPoint[1] + 2 * myScreenWidth * dy,
      myLookPoint[2] + 2 * myScreenWidth * dz};
    double[] zv = new double[]{-dx, -dy, -dz};
    double[] rv = new double[]{(dx == 0) ? 1 : 0, (dx == 0) ? 0 : 1, 0};
    double[] up = new double[3];
    VectorUtil.norm(zv);
    VectorUtil.norm(rv);

    VectorUtil.cross(zv, rv, up);
    VectorUtil.cross(zv, up, rv);
    VectorUtil.cross(zv, rv, up);
    myUpVector = up;
    myScreenDim = new int[]{width, height};
    calcMatrix();
  }

  public void lookAt(TriData tri, float[] voxelDim, int w, int h) {
    calcLook(tri, voxelDim, w, h);

    myEyePoint = new double[]{myLookPoint[0] + myScreenWidth, myLookPoint[1] + myScreenWidth,
      myLookPoint[2] + myScreenWidth};
    double[] zv = new double[]{-1, -1, -1};
    double[] rv = new double[]{1, 1, 0};
    double[] up = new double[3];
    VectorUtil.norm(zv);
    VectorUtil.norm(rv);

    VectorUtil.cross(zv, rv, up);
    VectorUtil.cross(zv, up, rv);
    VectorUtil.cross(zv, rv, up);
    myUpVector = up;
    myScreenDim = new int[]{w, h};
    calcMatrix();
  }

  int mStartx, mStarty;
  Matrix mStartMatrix;
  double[] mStartV = new double[3];
  double[] mMoveToV = new double[3];
  double[] mStartEyePoint;
  double[] mStartUpVector;
  Quaternion mQ = new Quaternion(0, 0, 0, 0);

  public void trackBallUP(int x, int y) {

  }

  public void trackBallDown(int x, int y) {
    mStartx = x;
    mStarty = y;
    ballToVec(x, y, mStartV);
    mStartEyePoint = Arrays.copyOf(myEyePoint, m.length);
    mStartUpVector = Arrays.copyOf(myUpVector, m.length);
    mStartMatrix = new Matrix(this);
    mStartMatrix.makeRotation();
  }

  public void trackBallMove(int x, int y) {
    ballToVec(x, y, mMoveToV);

    double angle = Quaternion.calcAngle(mStartV, mMoveToV);
    double[] axis = Quaternion.calcAxis(mStartV, mMoveToV);

    axis = mStartMatrix.vecmult(axis);

    mQ.set(angle, axis);

    VectorUtil.sub(myLookPoint, mStartEyePoint, myEyePoint);

    myEyePoint = mQ.rotateVec(myEyePoint);
    myUpVector = mQ.rotateVec(mStartUpVector);

    VectorUtil.sub(myLookPoint, myEyePoint, myEyePoint);
    calcMatrix();
  }

  void ballToVec(int x, int y, double[] v) {
    float ballRadius = Math.min(myScreenDim[0], myScreenDim[1]) * .4f;
    double cx = myScreenDim[0] / 2.;
    double cy = myScreenDim[1] / 2.;

    double dx = (cx - x) / ballRadius;
    double dy = (cy - y) / ballRadius;
    double scale = dx * dx + dy * dy;
    if (scale > 1) {
      scale = Math.sqrt(scale);
      dx = dx / scale;
      dy = dy / scale;
    }

    double dz = Math.sqrt(Math.abs(1 - (dx * dx + dy * dy)));
    v[0] = dx;
    v[1] = dy;
    v[2] = dz;
    VectorUtil.normalize(v);
  }

  public String getOrientationString(Rectangle rect) {
    String ret = "";

    ret += myEyePoint[0] + ",";
    ret += myEyePoint[1] + ",";
    ret += myEyePoint[2] + ",";
    ret += myUpVector[0] + ",";
    ret += myUpVector[1] + ",";
    ret += myUpVector[2] + ",";
    ret += myLookPoint[0] + ",";
    ret += myLookPoint[1] + ",";
    ret += myLookPoint[2] + ",";
    ret += myScreenWidth + ",";
    ret += myScreenDim[0] + ",";
    ret += myScreenDim[1] + ",";
    ret += rect.x + ",";
    ret += rect.y + ",";
    ret += rect.width + ",";
    ret += rect.height + ",";

    return ret;
  }

  public Rectangle parseOrientationString(String str) {
    String[] sp = str.split(",");
    int c = 0;

    myLookPoint = new double[3];
    myEyePoint[0] = Double.parseDouble(sp[c++]);
    myEyePoint[1] = Double.parseDouble(sp[c++]);
    myEyePoint[2] = Double.parseDouble(sp[c++]);
    myUpVector[0] = Double.parseDouble(sp[c++]);
    myUpVector[1] = Double.parseDouble(sp[c++]);
    myUpVector[2] = Double.parseDouble(sp[c++]);
    myLookPoint[0] = Double.parseDouble(sp[c++]);
    myLookPoint[1] = Double.parseDouble(sp[c++]);
    myLookPoint[2] = Double.parseDouble(sp[c++]);
    myScreenWidth = Double.parseDouble(sp[c++]);
    myScreenDim[0] = Integer.parseInt(sp[c++]);
    myScreenDim[1] = Integer.parseInt(sp[c++]);
    Rectangle rect = new Rectangle();
    rect.x = Integer.parseInt(sp[c++]);
    rect.y = Integer.parseInt(sp[c++]);
    rect.width = Integer.parseInt(sp[c++]);
    rect.height = Integer.parseInt(sp[c++]);
    calcMatrix();
    return rect;
  }
}
