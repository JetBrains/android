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

/**
 * This is a collection of code to rasterize code
 */
public class Rasterize {

  private final static int min(int x1, int x2, int x3) {
    return (x1 > x2) ? ((x2 > x3) ? x3 : x2) : ((x1 > x3) ? x3 : x1);
  }

  private final static int max(int x1, int x2, int x3) {
    return (x1 < x2) ? ((x2 < x3) ? x3 : x2) : ((x1 < x3) ? x3 : x1);
  }

  /**
   * Basic triangle renderer from
   * http://devmaster.net/forums/topic/1145-advanced-rasterization
   */

  public static void triangle(float[] zbuff, int w, int h,
                              float fx3, float fy3, float fz3,
                              float fx2, float fy2, float fz2,
                              float fx1, float fy1, float fz1) {

    if (((fx1 - fx2) * (fy3 - fy2) - (fy1 - fy2) * (fx3 - fx2)) < 0) {
      return;
    }
    // Code was generated using Maxima to solve the flowing equation:
    // string(solve([x1*dx+y1*dy+zoff=z1,x2*dx+y2*dy+zoff=z2,x3*dx+y3*dy+zoff=z3],[dx,dy,zoff]));

    double d = (fx1 * (fy3 - fy2) - fx2 * fy3 + fx3 * fy2 + (fx2 - fx3)
                                                            * fy1);

    if (d == 0) {
      return;
    }
    float dx = (float)(-(fy1 * (fz3 - fz2) - fy2 * fz3 + fy3 * fz2 + (fy2 - fy3)
                                                                     * fz1) / d);
    float dy = (float)((fx1 * (fz3 - fz2) - fx2 * fz3 + fx3 * fz2 + (fx2 - fx3)
                                                                    * fz1) / d);
    float zoff = (float)((fx1 * (fy3 * fz2 - fy2 * fz3) + fy1
                                                          * (fx2 * fz3 - fx3 * fz2) + (fx3 * fy2 - fx2 * fy3) * fz1) / d);

    // 28.4 fixed-point coordinates

    int Y1 = (int)(16.0f * fy1 + .5f);
    int Y2 = (int)(16.0f * fy2 + .5f);
    int Y3 = (int)(16.0f * fy3 + .5f);

    int X1 = (int)(16.0f * fx1 + .5f);
    int X2 = (int)(16.0f * fx2 + .5f);
    int X3 = (int)(16.0f * fx3 + .5f);

    int DX12 = X1 - X2;
    int DX23 = X2 - X3;
    int DX31 = X3 - X1;

    int DY12 = Y1 - Y2;
    int DY23 = Y2 - Y3;
    int DY31 = Y3 - Y1;

    int FDX12 = DX12 << 4;
    int FDX23 = DX23 << 4;
    int FDX31 = DX31 << 4;

    int FDY12 = DY12 << 4;
    int FDY23 = DY23 << 4;
    int FDY31 = DY31 << 4;

    int minx = (min(X1, X2, X3) + 0xF) >> 4;
    int maxx = (max(X1, X2, X3) + 0xF) >> 4;
    int miny = (min(Y1, Y2, Y3) + 0xF) >> 4;
    int maxy = (max(Y1, Y2, Y3) + 0xF) >> 4;

    if (miny < 0) {
      miny = 0;
    }
    if (minx < 0) {
      minx = 0;
    }
    if (maxx > w) {
      maxx = w;
    }
    if (maxy > h) {
      maxy = h;
    }
    int off = miny * w;

    int C1 = DY12 * X1 - DX12 * Y1;
    int C2 = DY23 * X2 - DX23 * Y2;
    int C3 = DY31 * X3 - DX31 * Y3;

    if (DY12 < 0 || (DY12 == 0 && DX12 > 0)) {
      C1++;
    }
    if (DY23 < 0 || (DY23 == 0 && DX23 > 0)) {
      C2++;
    }
    if (DY31 < 0 || (DY31 == 0 && DX31 > 0)) {
      C3++;
    }
    int CY1 = C1 + DX12 * (miny << 4) - DY12 * (minx << 4);
    int CY2 = C2 + DX23 * (miny << 4) - DY23 * (minx << 4);
    int CY3 = C3 + DX31 * (miny << 4) - DY31 * (minx << 4);

    for (int y = miny; y < maxy; y++) {
      int CX1 = CY1;
      int CX2 = CY2;
      int CX3 = CY3;
      float p = zoff + dy * y;
      for (int x = minx; x < maxx; x++) {
        if (CX1 > 0 && CX2 > 0 && CX3 > 0) {
          zbuff[x + off] = p + dx * x;
        }
        CX1 -= FDY12;
        CX2 -= FDY23;
        CX3 -= FDY31;
      }
      CY1 += FDX12;
      CY2 += FDX23;
      CY3 += FDX31;
      off += w;
    }
  }

  public static void triangleZBuffMin(float[] zbuff, int w, int h,
                                      float fx3, float fy3, float fz3,
                                      float fx2, float fy2, float fz2,
                                      float fx1, float fy1, float fz1) {
    if (((fx1 - fx2) * (fy3 - fy2) - (fy1 - fy2) * (fx3 - fx2)) < 0) {
      float tmpx = fx1;
      float tmpy = fy1;
      float tmpz = fz1;
      fx1 = fx2;
      fy1 = fy2;
      fz1 = fz2;
      fx2 = tmpx;
      fy2 = tmpy;
      fz2 = tmpz;
    }
    // using Maxima
    // string(solve([x1*dx+y1*dy+zoff=z1,x2*dx+y2*dy+zoff=z2,x3*dx+y3*dy+zoff=z3],[dx,dy,zoff]));
    double d = (fx1 * (fy3 - fy2) - fx2 * fy3 + fx3 * fy2 + (fx2 - fx3)
                                                            * fy1);

    if (d == 0) {
      return;
    }
    float dx = (float)(-(fy1 * (fz3 - fz2) - fy2 * fz3 + fy3 * fz2 + (fy2 - fy3)
                                                                     * fz1) / d);
    float dy = (float)((fx1 * (fz3 - fz2) - fx2 * fz3 + fx3 * fz2 + (fx2 - fx3)
                                                                    * fz1) / d);
    float zoff = (float)((fx1 * (fy3 * fz2 - fy2 * fz3) + fy1
                                                          * (fx2 * fz3 - fx3 * fz2) + (fx3 * fy2 - fx2 * fy3) * fz1) / d);

    // 28.4 fixed-point coordinates

    int Y1 = (int)(16.0f * fy1 + .5f);
    int Y2 = (int)(16.0f * fy2 + .5f);
    int Y3 = (int)(16.0f * fy3 + .5f);

    int X1 = (int)(16.0f * fx1 + .5f);
    int X2 = (int)(16.0f * fx2 + .5f);
    int X3 = (int)(16.0f * fx3 + .5f);

    int DX12 = X1 - X2;
    int DX23 = X2 - X3;
    int DX31 = X3 - X1;

    int DY12 = Y1 - Y2;
    int DY23 = Y2 - Y3;
    int DY31 = Y3 - Y1;

    int FDX12 = DX12 << 4;
    int FDX23 = DX23 << 4;
    int FDX31 = DX31 << 4;

    int FDY12 = DY12 << 4;
    int FDY23 = DY23 << 4;
    int FDY31 = DY31 << 4;

    int minx = (min(X1, X2, X3) + 0xF) >> 4;
    int maxx = (max(X1, X2, X3) + 0xF) >> 4;
    int miny = (min(Y1, Y2, Y3) + 0xF) >> 4;
    int maxy = (max(Y1, Y2, Y3) + 0xF) >> 4;

    if (miny < 0) {
      miny = 0;
    }
    if (minx < 0) {
      minx = 0;
    }
    if (maxx > w) {
      maxx = w;
    }
    if (maxy > h) {
      maxy = h;
    }
    int off = miny * w;

    int C1 = DY12 * X1 - DX12 * Y1;
    int C2 = DY23 * X2 - DX23 * Y2;
    int C3 = DY31 * X3 - DX31 * Y3;

    if (DY12 < 0 || (DY12 == 0 && DX12 > 0)) {
      C1++;
    }
    if (DY23 < 0 || (DY23 == 0 && DX23 > 0)) {
      C2++;
    }
    if (DY31 < 0 || (DY31 == 0 && DX31 > 0)) {
      C3++;
    }
    int CY1 = C1 + DX12 * (miny << 4) - DY12 * (minx << 4);
    int CY2 = C2 + DX23 * (miny << 4) - DY23 * (minx << 4);
    int CY3 = C3 + DX31 * (miny << 4) - DY31 * (minx << 4);

    for (int y = miny; y < maxy; y++) {
      int CX1 = CY1;
      int CX2 = CY2;
      int CX3 = CY3;
      float p = zoff + dy * y;
      for (int x = minx; x < maxx; x++) {
        if (CX1 > 0 && CX2 > 0 && CX3 > 0) {
          int point = x + off;
          float zval = p + dx * x;
          if (zbuff[point] > zval) {
            zbuff[point] = zval;
          }
        }
        CX1 -= FDY12;
        CX2 -= FDY23;
        CX3 -= FDY31;
      }
      CY1 += FDX12;
      CY2 += FDX23;
      CY3 += FDX31;
      off += w;
    }
  }

  /**
   * Render a myTexture including use of zbuffer
   */
  public static void render_perspectiveAffine(float[] zbuff, int[] rgb, int w, int h,
                                              float fx1, float fy1, float fz1,
                                              float fx2, float fy2, float fz2,
                                              float fx3, float fy3, float fz3,
                                              float tx1, float ty1,
                                              float tx2, float ty2,
                                              float tx3, float ty3,
                                              int[] texture, int tWidth, int tHeight, double[] matrix)

  {
    if (((fx1 - fx2) * (fy3 - fy2) - (fy1 - fy2) * (fx3 - fx2)) < 0) {
      float tmpx = fx1;
      float tmpy = fy1;
      float tmpz = fz1;
      fx1 = fx2;
      fy1 = fy2;
      fz1 = fz2;
      fx2 = tmpx;
      fy2 = tmpy;
      fz2 = tmpz;

      tmpx = tx1;
      tmpy = ty1;
      tx1 = tx2;
      ty1 = ty2;
      tx2 = tmpx;
      ty2 = tmpy;
    }
    else {
      return;
    }
    // using maxmima
    // string(solve([x1*dx+y1*dy+zoff=z1,x2*dx+y2*dy+zoff=z2,x3*dx+y3*dy+zoff=z3],[dx,dy,zoff]));
    double d = (fx1 * (fy3 - fy2) - fx2 * fy3 + fx3 * fy2 + (fx2 - fx3)
                                                            * fy1);

    if (d == 0) {
      return;
    }
    float dx = (float)(-(fy1 * (fz3 - fz2) - fy2 * fz3 + fy3 * fz2 + (fy2 - fy3)
                                                                     * fz1) / d);
    float dy = (float)((fx1 * (fz3 - fz2) - fx2 * fz3 + fx3 * fz2 + (fx2 - fx3)
                                                                    * fz1) / d);
    float zoff = (float)((fx1 * (fy3 * fz2 - fy2 * fz3) + fy1
                                                          * (fx2 * fz3 - fx3 * fz2) + (fx3 * fy2 - fx2 * fy3) * fz1) / d);

    float diffuse = 1;
    if (true) { // diffuse light

      float dx12 = fx1 - fx2;
      float dy12 = fy1 - fy2;
      float dz12 = fz1 - fz2;

      float dx13 = fx1 - fx3;
      float dy13 = fy1 - fy3;
      float dz13 = fz1 - fz3;
      float normal_x = dy12 * dz13 - dy13 * dz12; // cross
      float normal_y = dz12 * dx13 - dz13 * dx12;
      float normal_z = dx12 * dy13 - dx13 * dy12;
      float norm = normal_x * normal_x + normal_y * normal_y + normal_z * normal_z;
      normal_z /= Math.sqrt(norm);
      diffuse = -normal_z;
      if (diffuse < 0) diffuse = 0;
      diffuse = (diffuse + 1) / 2;
    }

    float dsx1 = fx1 - fx2, dsx2 = fx2 - fx3, dsx3 = fx3 - fx1;
    float dsy1 = fy1 - fy2, dsy2 = fy2 - fy3, dsy3 = fy3 - fy1;
    float dtx1 = tx1 - tx2, dtx2 = tx2 - tx3, dtx3 = tx3 - tx3;
    float dty1 = ty1 - ty2, dty2 = ty2 - ty3, dty3 = ty3 - ty3;
    float d12 = dsx1 * dsy2 - dsx2 * dsy1;
    float d23 = dsx2 * dsy3 - dsx3 * dsy2;
    float d31 = dsx3 * dsy1 - dsx1 * dsy3;
    float delta = d12;


    // float delta = (dsx1 * dsy2 - dsx2 * dsy1);
    float dtx_dsx = (dsy2 * dtx1 - dsy1 * dtx2) / delta;
    float dtx_dsy = (dsx1 * dtx2 - dsx2 * dtx1) / delta;
    float dty_dsx = (dsy2 * dty1 - dsy1 * dty2) / delta;
    float dty_dsy = (dsx1 * dty2 - dsx2 * dty1) / delta;

    float txoff = tx1 - dtx_dsx * fx1 - dtx_dsy * fy1;
    float tyoff = ty1 - dty_dsx * fx1 - dty_dsy * fy1;

    // 28.4 fixed-point coordinates

    int Y1 = (int)(16.0f * fy1 + .5f);
    int Y2 = (int)(16.0f * fy2 + .5f);
    int Y3 = (int)(16.0f * fy3 + .5f);

    int X1 = (int)(16.0f * fx1 + .5f);
    int X2 = (int)(16.0f * fx2 + .5f);
    int X3 = (int)(16.0f * fx3 + .5f);

    int DX12 = X1 - X2;
    int DX23 = X2 - X3;
    int DX31 = X3 - X1;

    int DY12 = Y1 - Y2;
    int DY23 = Y2 - Y3;
    int DY31 = Y3 - Y1;

    int FDX12 = DX12 << 4;
    int FDX23 = DX23 << 4;
    int FDX31 = DX31 << 4;

    int FDY12 = DY12 << 4;
    int FDY23 = DY23 << 4;
    int FDY31 = DY31 << 4;

    int minx = (min(X1, X2, X3) + 0xF) >> 4;
    int maxx = (max(X1, X2, X3) + 0xF) >> 4;
    int miny = (min(Y1, Y2, Y3) + 0xF) >> 4;
    int maxy = (max(Y1, Y2, Y3) + 0xF) >> 4;

    if (miny < 0) {
      miny = 0;
    }
    if (minx < 0) {
      minx = 0;
    }
    if (maxx > w) {
      maxx = w;
    }
    if (maxy > h) {
      maxy = h;
    }
    int off = miny * w;

    int C1 = DY12 * X1 - DX12 * Y1;
    int C2 = DY23 * X2 - DX23 * Y2;
    int C3 = DY31 * X3 - DX31 * Y3;

    if (DY12 < 0 || (DY12 == 0 && DX12 > 0)) {
      C1++;
    }
    if (DY23 < 0 || (DY23 == 0 && DX23 > 0)) {
      C2++;
    }
    if (DY31 < 0 || (DY31 == 0 && DX31 > 0)) {
      C3++;
    }
    int CY1 = C1 + DX12 * (miny << 4) - DY12 * (minx << 4);
    int CY2 = C2 + DX23 * (miny << 4) - DY23 * (minx << 4);
    int CY3 = C3 + DX31 * (miny << 4) - DY31 * (minx << 4);

    for (int y = miny; y < maxy; y++) {
      int CX1 = CY1;
      int CX2 = CY2;
      int CX3 = CY3;
      float p = zoff + dy * y;
      float tx_yoff = dtx_dsy * y + txoff; // add in dtx_dsx * x in inner loop
      float ty_yoff = dty_dsy * y + tyoff; // add in dty_dsx * x in inner loop
      for (int x = minx; x < maxx; x++) {
        if (CX1 > 0 && CX2 > 0 && CX3 > 0) {
          int point = x + off;
          float zval = p + dx * x;
          if (zbuff[point] > zval) {
            zbuff[point] = zval;
            //            int tx = clamp((int) (tx_yoff + dtx_dsx * x + dtx_dsz * zval), tWidth - 1);
            //            int ty = clamp((int) (ty_yoff + dty_dsx * x + dty_dsz * zval), tHeight - 1);
            int tx = clamp((int)(tx_yoff + dtx_dsx * x), tWidth - 1);
            int ty = clamp((int)(ty_yoff + dty_dsx * x), tHeight - 1);
            try {
              //							rgb[point] =   0x10101*clamp(255/(1+Math.abs(ty)),255);
              rgb[point] = shade(texture[tx + ty * tWidth], diffuse);
            }
            catch (Exception e) {
              System.err.println(" " + tx + ", " + ty);
            }
          }
        }
        CX1 -= FDY12;
        CX2 -= FDY23;
        CX3 -= FDY31;
      }
      CY1 += FDX12;
      CY2 += FDX23;
      CY3 += FDX31;
      off += w;
    }
  }

  /**
   * Render a myTexture including use of zbuffer
   */
  public static void render_perspective(float[] zbuff, int[] rgb, int w, int h,
                                              float fx1, float fy1, float fz1,
                                              float fx2, float fy2, float fz2,
                                              float fx3, float fy3, float fz3,
                                              float tx1, float ty1,
                                              float tx2, float ty2,
                                              float tx3, float ty3,
                                              int[] texture, int tWidth, int tHeight, double[] matrix)

  {
    if (((fx1 - fx2) * (fy3 - fy2) - (fy1 - fy2) * (fx3 - fx2)) < 0) {
      float tmpx = fx1;
      float tmpy = fy1;
      float tmpz = fz1;
      fx1 = fx2;
      fy1 = fy2;
      fz1 = fz2;
      fx2 = tmpx;
      fy2 = tmpy;
      fz2 = tmpz;

      tmpx = tx1;
      tmpy = ty1;
      tx1 = tx2;
      ty1 = ty2;
      tx2 = tmpx;
      ty2 = tmpy;
    }
    else {
      return;
    }
    // using maxmima
    // string(solve([x1*dx+y1*dy+zoff=z1,x2*dx+y2*dy+zoff=z2,x3*dx+y3*dy+zoff=z3],[dx,dy,zoff]));
    double d = (fx1 * (fy3 - fy2) - fx2 * fy3 + fx3 * fy2 + (fx2 - fx3)
                                                            * fy1);

    if (d == 0) {
      return;
    }
    float dx = (float)(-(fy1 * (fz3 - fz2) - fy2 * fz3 + fy3 * fz2 + (fy2 - fy3)
                                                                     * fz1) / d);
    float dy = (float)((fx1 * (fz3 - fz2) - fx2 * fz3 + fx3 * fz2 + (fx2 - fx3)
                                                                    * fz1) / d);
    float zoff = (float)((fx1 * (fy3 * fz2 - fy2 * fz3) + fy1
                                                          * (fx2 * fz3 - fx3 * fz2) + (fx3 * fy2 - fx2 * fy3) * fz1) / d);

    float diffuse = 1;
    if (true) { // diffuse light

      float dx12 = fx1 - fx2;
      float dy12 = fy1 - fy2;
      float dz12 = fz1 - fz2;

      float dx13 = fx1 - fx3;
      float dy13 = fy1 - fy3;
      float dz13 = fz1 - fz3;
      float normal_x = dy12 * dz13 - dy13 * dz12; // cross
      float normal_y = dz12 * dx13 - dz13 * dx12;
      float normal_z = dx12 * dy13 - dx13 * dy12;
      float norm = normal_x * normal_x + normal_y * normal_y + normal_z * normal_z;
      normal_z /= Math.sqrt(norm);
      diffuse = -normal_z;
      if (diffuse < 0) diffuse = 0;
      diffuse = (diffuse + 1) / 2;
    }

    float dsx1 = fx1 - fx2, dsx2 = fx2 - fx3, dsx3 = fx3 - fx1;
    float dsy1 = fy1 - fy2, dsy2 = fy2 - fy3, dsy3 = fy3 - fy1;
    float dtx1 = tx1 - tx2, dtx2 = tx2 - tx3, dtx3 = tx3 - tx3;
    float dty1 = ty1 - ty2, dty2 = ty2 - ty3, dty3 = ty3 - ty3;
    float d12 = dsx1 * dsy2 - dsx2 * dsy1;
    float d23 = dsx2 * dsy3 - dsx3 * dsy2;
    float d31 = dsx3 * dsy1 - dsx1 * dsy3;

    float dz1 = (1/fz1 - 1/fz2);
    float dz2 = (1/fz2 - 1/fz3);
    float dz3 = (1/fz3 - 1/fz1);
    float dtxz2 = (tx2/fz2 - tx3/fz3);
    float dtxz1 = (tx1/fz1 - tx2/fz2);
    //float dtxz2 = (tx2/fz2 - tx3/fz3);
    float dtxz3 = (tx3/fz3 - tx1/fz1);
    float dtyz1 = (ty1/fz1 - ty2/fz2);
    float dtyz2 = (ty2/fz2 - ty3/fz3);
    float dtyz3 = (ty3/fz3 - ty1/fz1);
    float dtz12 = dtxz1 * dtyz2 - dtxz2 * dtyz1;

    float deltatz = dtz12; // TODO we should pick deltatz between dtz12,dtz23,dtz31 based biggest value
    float dz_dsx = dz1/dsx1;
    float dz_dsy = dz1/dsy1;

    float dtx_dsx = (dsy2 * dtxz1 - dsy1 * dtxz2) / deltatz;
    float dtx_dsy = (dsx1 * dtxz2 - dsx2 * dtxz1) / deltatz;
    float dty_dsx = (dsy2 * dtyz1 - dsy1 * dtyz2) / deltatz;
    float dty_dsy = (dsx1 * dtyz2 - dsx2 * dtyz1) / deltatz;

    float delta = d12;

    // float delta = (dsx1 * dsy2 - dsx2 * dsy1);


    float txoff = tx1 - dtx_dsx * fx1 - dtx_dsy * fy1;
    float tyoff = ty1 - dty_dsx * fx1 - dty_dsy * fy1;
    float off1_z = 1/fz1 - dz_dsx * fx1 - dz_dsy * fy1;

    // 28.4 fixed-point coordinates

    int Y1 = (int)(16.0f * fy1 + .5f);
    int Y2 = (int)(16.0f * fy2 + .5f);
    int Y3 = (int)(16.0f * fy3 + .5f);

    int X1 = (int)(16.0f * fx1 + .5f);
    int X2 = (int)(16.0f * fx2 + .5f);
    int X3 = (int)(16.0f * fx3 + .5f);

    int DX12 = X1 - X2;
    int DX23 = X2 - X3;
    int DX31 = X3 - X1;

    int DY12 = Y1 - Y2;
    int DY23 = Y2 - Y3;
    int DY31 = Y3 - Y1;

    int FDX12 = DX12 << 4;
    int FDX23 = DX23 << 4;
    int FDX31 = DX31 << 4;

    int FDY12 = DY12 << 4;
    int FDY23 = DY23 << 4;
    int FDY31 = DY31 << 4;

    int minx = (min(X1, X2, X3) + 0xF) >> 4;
    int maxx = (max(X1, X2, X3) + 0xF) >> 4;
    int miny = (min(Y1, Y2, Y3) + 0xF) >> 4;
    int maxy = (max(Y1, Y2, Y3) + 0xF) >> 4;

    if (miny < 0) {
      miny = 0;
    }
    if (minx < 0) {
      minx = 0;
    }
    if (maxx > w) {
      maxx = w;
    }
    if (maxy > h) {
      maxy = h;
    }
    int off = miny * w;

    int C1 = DY12 * X1 - DX12 * Y1;
    int C2 = DY23 * X2 - DX23 * Y2;
    int C3 = DY31 * X3 - DX31 * Y3;

    if (DY12 < 0 || (DY12 == 0 && DX12 > 0)) {
      C1++;
    }
    if (DY23 < 0 || (DY23 == 0 && DX23 > 0)) {
      C2++;
    }
    if (DY31 < 0 || (DY31 == 0 && DX31 > 0)) {
      C3++;
    }
    int CY1 = C1 + DX12 * (miny << 4) - DY12 * (minx << 4);
    int CY2 = C2 + DX23 * (miny << 4) - DY23 * (minx << 4);
    int CY3 = C3 + DX31 * (miny << 4) - DY31 * (minx << 4);

    for (int y = miny; y < maxy; y++) {
      int CX1 = CY1;
      int CX2 = CY2;
      int CX3 = CY3;
      float p = zoff + dy * y;
      float tx_yoff = dtx_dsy * y + txoff; // add in dtx_dsx * x in inner loop
      float ty_yoff = dty_dsy * y + tyoff; // add in dty_dsx * x in inner loop
      float t_z_yoff = dz_dsy * y + off1_z;
      for (int x = minx; x < maxx; x++) {
        if (CX1 > 0 && CX2 > 0 && CX3 > 0) {
          int point = x + off;
          float zval = p + dx * x;
          if (zbuff[point] > zval) {
            zbuff[point] = zval;

            int tx = clamp((int)((tx_yoff + dtx_dsx * x)/(t_z_yoff+ dz_dsx*x)), tWidth - 1);
            int ty = clamp((int)((ty_yoff + dty_dsx * x)/(t_z_yoff+ dz_dsx*x)), tHeight - 1);
            try {
              //							rgb[point] =   0x10101*clamp(255/(1+Math.abs(ty)),255);
              rgb[point] = shade(texture[tx + ty * tWidth], diffuse);
            }
            catch (Exception e) {
              System.err.println(" " + tx + ", " + ty);
            }
          }
        }
        CX1 -= FDY12;
        CX2 -= FDY23;
        CX3 -= FDY31;
      }
      CY1 += FDX12;
      CY2 += FDX23;
      CY3 += FDX31;
      off += w;
    }
  }

  /**
   * Render a myTexture including use of zbuffer
   */
  public static void render(float[] zbuff, int[] rgb, int w, int h,
                            float fx1, float fy1, float fz1,
                            float fx2, float fy2, float fz2,
                            float fx3, float fy3, float fz3,
                            float tx1, float ty1,
                            float tx2, float ty2,
                            float tx3, float ty3,
                            int[] texture, int tWidth, int tHeight, double[] matrix)

  {
    if (((fx1 - fx2) * (fy3 - fy2) - (fy1 - fy2) * (fx3 - fx2)) < 0) {
      float tmpx = fx1;
      float tmpy = fy1;
      float tmpz = fz1;
      fx1 = fx2;
      fy1 = fy2;
      fz1 = fz2;
      fx2 = tmpx;
      fy2 = tmpy;
      fz2 = tmpz;

      tmpx = tx1;
      tmpy = ty1;
      tx1 = tx2;
      ty1 = ty2;
      tx2 = tmpx;
      ty2 = tmpy;
    }
    // using Maxima
    // string(solve([x1*dx+y1*dy+zoff=z1,x2*dx+y2*dy+zoff=z2,x3*dx+y3*dy+zoff=z3],[dx,dy,zoff]));
    double d = (fx1 * (fy3 - fy2) - fx2 * fy3 + fx3 * fy2 + (fx2 - fx3)
                                                            * fy1);

    if (d == 0) {
      return;
    }
    float dx = (float)(-(fy1 * (fz3 - fz2) - fy2 * fz3 + fy3 * fz2 + (fy2 - fy3)
                                                                     * fz1) / d);
    float dy = (float)((fx1 * (fz3 - fz2) - fx2 * fz3 + fx3 * fz2 + (fx2 - fx3)
                                                                    * fz1) / d);
    float zoff = (float)((fx1 * (fy3 * fz2 - fy2 * fz3) + fy1
                                                          * (fx2 * fz3 - fx3 * fz2) + (fx3 * fy2 - fx2 * fy3) * fz1) / d);

    float diffuse = 1;
    if (true) { // diffuse light

      float dx12 = fx1 - fx2;
      float dy12 = fy1 - fy2;
      float dz12 = fz1 - fz2;

      float dx13 = fx1 - fx3;
      float dy13 = fy1 - fy3;
      float dz13 = fz1 - fz3;
      float normal_x = dy12 * dz13 - dy13 * dz12; // cross
      float normal_y = dz12 * dx13 - dz13 * dx12;
      float normal_z = dx12 * dy13 - dx13 * dy12;
      float norm = normal_x * normal_x + normal_y * normal_y + normal_z * normal_z;
      normal_z /= Math.sqrt(norm);
      diffuse = -normal_z;
      if (diffuse < 0) diffuse = 0;
      diffuse = (diffuse + 1) / 2;
    }
    float dtx_dsx = (float)matrix[0];
    float dtx_dsy = (float)matrix[1];
    float dtx_dsz = (float)matrix[2];
    float dty_dsx = (float)matrix[4];
    float dty_dsy = (float)matrix[5];
    float dty_dsz = (float)matrix[6];

    float txoff = tx1 - dtx_dsx * fx1 - dtx_dsy * fy1 - dtx_dsz * fz1;
    float tyoff = ty1 - dty_dsx * fx1 - dty_dsy * fy1 - dty_dsz * fz1;

    // 28.4 fixed-point coordinates

    int Y1 = (int)(16.0f * fy1 + .5f);
    int Y2 = (int)(16.0f * fy2 + .5f);
    int Y3 = (int)(16.0f * fy3 + .5f);

    int X1 = (int)(16.0f * fx1 + .5f);
    int X2 = (int)(16.0f * fx2 + .5f);
    int X3 = (int)(16.0f * fx3 + .5f);

    int DX12 = X1 - X2;
    int DX23 = X2 - X3;
    int DX31 = X3 - X1;

    int DY12 = Y1 - Y2;
    int DY23 = Y2 - Y3;
    int DY31 = Y3 - Y1;

    int FDX12 = DX12 << 4;
    int FDX23 = DX23 << 4;
    int FDX31 = DX31 << 4;

    int FDY12 = DY12 << 4;
    int FDY23 = DY23 << 4;
    int FDY31 = DY31 << 4;

    int minx = (min(X1, X2, X3) + 0xF) >> 4;
    int maxx = (max(X1, X2, X3) + 0xF) >> 4;
    int miny = (min(Y1, Y2, Y3) + 0xF) >> 4;
    int maxy = (max(Y1, Y2, Y3) + 0xF) >> 4;

    if (miny < 0) {
      miny = 0;
    }
    if (minx < 0) {
      minx = 0;
    }
    if (maxx > w) {
      maxx = w;
    }
    if (maxy > h) {
      maxy = h;
    }
    int off = miny * w;

    int C1 = DY12 * X1 - DX12 * Y1;
    int C2 = DY23 * X2 - DX23 * Y2;
    int C3 = DY31 * X3 - DX31 * Y3;

    if (DY12 < 0 || (DY12 == 0 && DX12 > 0)) {
      C1++;
    }
    if (DY23 < 0 || (DY23 == 0 && DX23 > 0)) {
      C2++;
    }
    if (DY31 < 0 || (DY31 == 0 && DX31 > 0)) {
      C3++;
    }
    int CY1 = C1 + DX12 * (miny << 4) - DY12 * (minx << 4);
    int CY2 = C2 + DX23 * (miny << 4) - DY23 * (minx << 4);
    int CY3 = C3 + DX31 * (miny << 4) - DY31 * (minx << 4);

    for (int y = miny; y < maxy; y++) {
      int CX1 = CY1;
      int CX2 = CY2;
      int CX3 = CY3;
      float p = zoff + dy * y;
      float tx_yoff = dtx_dsy * y + txoff; // add in dtx_dsx * x in inner loop
      float ty_yoff = dty_dsy * y + tyoff; // add in dty_dsx * x in inner loop
      for (int x = minx; x < maxx; x++) {
        if (CX1 > 0 && CX2 > 0 && CX3 > 0) {
          int point = x + off;
          float zval = p + dx * x;
          if (zbuff[point] > zval) {
            zbuff[point] = zval;
            int tx = clamp((int)(tx_yoff + dtx_dsx * x + dtx_dsz * zval), tWidth - 1);
            int ty = clamp((int)(ty_yoff + dty_dsx * x + dty_dsz * zval), tHeight - 1);
            try {
              rgb[point] = shade(texture[tx + ty * tWidth], diffuse);
            }
            catch (Exception e) {
              System.err.println(" " + tx + ", " + ty);
            }
          }
        }
        CX1 -= FDY12;
        CX2 -= FDY23;
        CX3 -= FDY31;
      }
      CY1 += FDX12;
      CY2 += FDX23;
      CY3 += FDX31;
      off += w;
    }
  }

  /**
   * Render a myTexture including use of zbuffer
   */
  public static void flat(float[] zbuff, int[] rgb, int w, int h,
                          float fx1, float fy1, float fz1,
                          float fx2, float fy2, float fz2,
                          float fx3, float fy3, float fz3,
                          int color)

  {
    if (((fx1 - fx2) * (fy3 - fy2) - (fy1 - fy2) * (fx3 - fx2)) < 0) {
      float tmpx = fx1;
      float tmpy = fy1;
      float tmpz = fz1;
      fx1 = fx2;
      fy1 = fy2;
      fz1 = fz2;
      fx2 = tmpx;
      fy2 = tmpy;
      fz2 = tmpz;
    }
    // using Maxima
    // string(solve([x1*dx+y1*dy+zoff=z1,x2*dx+y2*dy+zoff=z2,x3*dx+y3*dy+zoff=z3],[dx,dy,zoff]));
    double d = (fx1 * (fy3 - fy2) - fx2 * fy3 + fx3 * fy2 + (fx2 - fx3)
                                                            * fy1);

    if (d == 0) {
      return;
    }
    float dx = (float)(-(fy1 * (fz3 - fz2) - fy2 * fz3 + fy3 * fz2 + (fy2 - fy3)
                                                                     * fz1) / d);
    float dy = (float)((fx1 * (fz3 - fz2) - fx2 * fz3 + fx3 * fz2 + (fx2 - fx3)
                                                                    * fz1) / d);
    float zoff = (float)((fx1 * (fy3 * fz2 - fy2 * fz3) + fy1
                                                          * (fx2 * fz3 - fx3 * fz2) + (fx3 * fy2 - fx2 * fy3) * fz1) / d);

    float diffuse = 1;
    if (true) { // diffuse light

      float dx12 = fx1 - fx2;
      float dy12 = fy1 - fy2;
      float dz12 = fz1 - fz2;

      float dx13 = fx1 - fx3;
      float dy13 = fy1 - fy3;
      float dz13 = fz1 - fz3;
      float normal_x = dy12 * dz13 - dy13 * dz12; // cross
      float normal_y = dz12 * dx13 - dz13 * dx12;
      float normal_z = dx12 * dy13 - dx13 * dy12;
      float norm = normal_x * normal_x + normal_y * normal_y + normal_z * normal_z;
      normal_z /= Math.sqrt(norm);
      diffuse = -normal_z;
      if (diffuse < 0) diffuse = 0;
      diffuse = (diffuse + 1) / 2;
    }

    // 28.4 fixed-point coordinates

    int Y1 = (int)(16.0f * fy1 + .5f);
    int Y2 = (int)(16.0f * fy2 + .5f);
    int Y3 = (int)(16.0f * fy3 + .5f);

    int X1 = (int)(16.0f * fx1 + .5f);
    int X2 = (int)(16.0f * fx2 + .5f);
    int X3 = (int)(16.0f * fx3 + .5f);

    int DX12 = X1 - X2;
    int DX23 = X2 - X3;
    int DX31 = X3 - X1;

    int DY12 = Y1 - Y2;
    int DY23 = Y2 - Y3;
    int DY31 = Y3 - Y1;

    int FDX12 = DX12 << 4;
    int FDX23 = DX23 << 4;
    int FDX31 = DX31 << 4;

    int FDY12 = DY12 << 4;
    int FDY23 = DY23 << 4;
    int FDY31 = DY31 << 4;

    int minx = (min(X1, X2, X3) + 0xF) >> 4;
    int maxx = (max(X1, X2, X3) + 0xF) >> 4;
    int miny = (min(Y1, Y2, Y3) + 0xF) >> 4;
    int maxy = (max(Y1, Y2, Y3) + 0xF) >> 4;

    if (miny < 0) {
      miny = 0;
    }
    if (minx < 0) {
      minx = 0;
    }
    if (maxx > w) {
      maxx = w;
    }
    if (maxy > h) {
      maxy = h;
    }
    int off = miny * w;

    int C1 = DY12 * X1 - DX12 * Y1;
    int C2 = DY23 * X2 - DX23 * Y2;
    int C3 = DY31 * X3 - DX31 * Y3;

    if (DY12 < 0 || (DY12 == 0 && DX12 > 0)) {
      C1++;
    }
    if (DY23 < 0 || (DY23 == 0 && DX23 > 0)) {
      C2++;
    }
    if (DY31 < 0 || (DY31 == 0 && DX31 > 0)) {
      C3++;
    }
    int CY1 = C1 + DX12 * (miny << 4) - DY12 * (minx << 4);
    int CY2 = C2 + DX23 * (miny << 4) - DY23 * (minx << 4);
    int CY3 = C3 + DX31 * (miny << 4) - DY31 * (minx << 4);

    for (int y = miny; y < maxy; y++) {
      int CX1 = CY1;
      int CX2 = CY2;
      int CX3 = CY3;
      float p = zoff + dy * y;

      for (int x = minx; x < maxx; x++) {
        if (CX1 > 0 && CX2 > 0 && CX3 > 0) {
          int point = x + off;
          float zval = p + dx * x;
          if (zbuff[point] > zval) {
            zbuff[point] = zval;

            rgb[point] = shade(color, diffuse);
          }
        }
        CX1 -= FDY12;
        CX2 -= FDY23;
        CX3 -= FDY31;
      }
      CY1 += FDX12;
      CY2 += FDX23;
      CY3 += FDX31;
      off += w;
    }
  }

  private static int shade(int texture, float diffuse) {
    int b = texture & 0xFF;
    int g = (texture >> 8) & 0xFF;
    int r = (texture >> 16) & 0xFF;
    r *= diffuse;
    g *= diffuse;
    b *= diffuse;
    return (r << 16) | (g << 8) | b;
  }

  /**
   * Efficient clamping between 0 and N
   */
  private static int clamp(int c, int N) {
    c &= ~(c >> 31);
    c -= N;
    c &= (c >> 31);
    c += N;
    return c;
  }

  /**
   * calculation of ZBuffer
   */

  public static void triangleZBuffMax(float[] zbuff, int w, int h, float fx3,
                                      float fy3, float fz3, float fx2, float fy2, float fz2, float fx1,
                                      float fy1, float fz1)

  {
    if (((fx1 - fx2) * (fy3 - fy2) - (fy1 - fy2) * (fx3 - fx2)) < 0) {
      float tmpx = fx1;
      float tmpy = fy1;
      float tmpz = fz1;
      fx1 = fx2;
      fy1 = fy2;
      fz1 = fz2;
      fx2 = tmpx;
      fy2 = tmpy;
      fz2 = tmpz;
    }
    // using Maxima
    // string(solve([x1*dx+y1*dy+zoff=z1,x2*dx+y2*dy+zoff=z2,x3*dx+y3*dy+zoff=z3],[dx,dy,zoff]));
    double d = (fx1 * (fy3 - fy2) - fx2 * fy3 + fx3 * fy2 + (fx2 - fx3)
                                                            * fy1);

    if (d == 0) {
      return;
    }
    float dx = (float)(-(fy1 * (fz3 - fz2) - fy2 * fz3 + fy3 * fz2 + (fy2 - fy3)
                                                                     * fz1) / d);
    float dy = (float)((fx1 * (fz3 - fz2) - fx2 * fz3 + fx3 * fz2 + (fx2 - fx3)
                                                                    * fz1) / d);
    float zoff = (float)((fx1 * (fy3 * fz2 - fy2 * fz3) + fy1
                                                          * (fx2 * fz3 - fx3 * fz2) + (fx3 * fy2 - fx2 * fy3) * fz1) / d);

    // 28.4 fixed-point coordinates

    int Y1 = (int)(16.0f * fy1 + .5f);
    int Y2 = (int)(16.0f * fy2 + .5f);
    int Y3 = (int)(16.0f * fy3 + .5f);

    int X1 = (int)(16.0f * fx1 + .5f);
    int X2 = (int)(16.0f * fx2 + .5f);
    int X3 = (int)(16.0f * fx3 + .5f);

    int DX12 = X1 - X2;
    int DX23 = X2 - X3;
    int DX31 = X3 - X1;

    int DY12 = Y1 - Y2;
    int DY23 = Y2 - Y3;
    int DY31 = Y3 - Y1;

    int FDX12 = DX12 << 4;
    int FDX23 = DX23 << 4;
    int FDX31 = DX31 << 4;

    int FDY12 = DY12 << 4;
    int FDY23 = DY23 << 4;
    int FDY31 = DY31 << 4;

    int minx = (min(X1, X2, X3) + 0xF) >> 4;
    int maxx = (max(X1, X2, X3) + 0xF) >> 4;
    int miny = (min(Y1, Y2, Y3) + 0xF) >> 4;
    int maxy = (max(Y1, Y2, Y3) + 0xF) >> 4;

    if (miny < 0) {
      miny = 0;
    }
    if (minx < 0) {
      minx = 0;
    }
    if (maxx > w) {
      maxx = w;
    }
    if (maxy > h) {
      maxy = h;
    }
    int off = miny * w;

    int C1 = DY12 * X1 - DX12 * Y1;
    int C2 = DY23 * X2 - DX23 * Y2;
    int C3 = DY31 * X3 - DX31 * Y3;

    if (DY12 < 0 || (DY12 == 0 && DX12 > 0)) {
      C1++;
    }
    if (DY23 < 0 || (DY23 == 0 && DX23 > 0)) {
      C2++;
    }
    if (DY31 < 0 || (DY31 == 0 && DX31 > 0)) {
      C3++;
    }
    int CY1 = C1 + DX12 * (miny << 4) - DY12 * (minx << 4);
    int CY2 = C2 + DX23 * (miny << 4) - DY23 * (minx << 4);
    int CY3 = C3 + DX31 * (miny << 4) - DY31 * (minx << 4);

    for (int y = miny; y < maxy; y++) {
      int CX1 = CY1;
      int CX2 = CY2;
      int CX3 = CY3;
      float p = zoff + dy * y;
      for (int x = minx; x < maxx; x++) {
        if (CX1 > 0 && CX2 > 0 && CX3 > 0) {
          int point = x + off;
          float zval = p + dx * x;
          if (zbuff[point] < zval) {
            zbuff[point] = zval;
          }
        }
        CX1 -= FDY12;
        CX2 -= FDY23;
        CX3 -= FDY31;
      }
      CY1 += FDX12;
      CY2 += FDX23;
      CY3 += FDX31;
      off += w;
    }
  }

  public static void triangleZBuffMinMax(float[] minz, float[] maxz, int w, int h, float fx3,
                                         float fy3, float fz3, float fx2, float fy2, float fz2, float fx1,
                                         float fy1, float fz1)

  {

    if (((fx1 - fx2) * (fy3 - fy2) - (fy1 - fy2) * (fx3 - fx2)) < 0) {
      float tmpx = fx1;
      float tmpy = fy1;
      float tmpz = fz1;
      fx1 = fx2;
      fy1 = fy2;
      fz1 = fz2;
      fx2 = tmpx;
      fy2 = tmpy;
      fz2 = tmpz;
    }
    // Code was generated using Maxima to solve the flowing equation:
    // string(solve([x1*dx+y1*dy+zoff=z1,x2*dx+y2*dy+zoff=z2,x3*dx+y3*dy+zoff=z3],[dx,dy,zoff]));

    double d = (fx1 * (fy3 - fy2) - fx2 * fy3 + fx3 * fy2 + (fx2 - fx3)
                                                            * fy1);

    if (d == 0) {
      return;
    }
    float dx = (float)(-(fy1 * (fz3 - fz2) - fy2 * fz3 + fy3 * fz2 + (fy2 - fy3)
                                                                     * fz1) / d);
    float dy = (float)((fx1 * (fz3 - fz2) - fx2 * fz3 + fx3 * fz2 + (fx2 - fx3)
                                                                    * fz1) / d);
    float zoff = (float)((fx1 * (fy3 * fz2 - fy2 * fz3) + fy1
                                                          * (fx2 * fz3 - fx3 * fz2) + (fx3 * fy2 - fx2 * fy3) * fz1) / d);

    // 28.4 fixed-point coordinates

    int Y1 = (int)(16.0f * fy1 + .5f);
    int Y2 = (int)(16.0f * fy2 + .5f);
    int Y3 = (int)(16.0f * fy3 + .5f);

    int X1 = (int)(16.0f * fx1 + .5f);
    int X2 = (int)(16.0f * fx2 + .5f);
    int X3 = (int)(16.0f * fx3 + .5f);

    int DX12 = X1 - X2;
    int DX23 = X2 - X3;
    int DX31 = X3 - X1;

    int DY12 = Y1 - Y2;
    int DY23 = Y2 - Y3;
    int DY31 = Y3 - Y1;

    int FDX12 = DX12 << 4;
    int FDX23 = DX23 << 4;
    int FDX31 = DX31 << 4;

    int FDY12 = DY12 << 4;
    int FDY23 = DY23 << 4;
    int FDY31 = DY31 << 4;

    int minx = (min(X1, X2, X3) + 0xF) >> 4;
    int maxx = (max(X1, X2, X3) + 0xF) >> 4;
    int miny = (min(Y1, Y2, Y3) + 0xF) >> 4;
    int maxy = (max(Y1, Y2, Y3) + 0xF) >> 4;

    if (miny < 0) {
      miny = 0;
    }
    if (minx < 0) {
      minx = 0;
    }
    if (maxx > w) {
      maxx = w;
    }
    if (maxy > h) {
      maxy = h;
    }
    int off = miny * w;

    int C1 = DY12 * X1 - DX12 * Y1;
    int C2 = DY23 * X2 - DX23 * Y2;
    int C3 = DY31 * X3 - DX31 * Y3;

    if (DY12 < 0 || (DY12 == 0 && DX12 > 0)) {
      C1++;
    }
    if (DY23 < 0 || (DY23 == 0 && DX23 > 0)) {
      C2++;
    }
    if (DY31 < 0 || (DY31 == 0 && DX31 > 0)) {
      C3++;
    }
    int CY1 = C1 + DX12 * (miny << 4) - DY12 * (minx << 4);
    int CY2 = C2 + DX23 * (miny << 4) - DY23 * (minx << 4);
    int CY3 = C3 + DX31 * (miny << 4) - DY31 * (minx << 4);

    for (int y = miny; y < maxy; y++) {
      int CX1 = CY1;
      int CX2 = CY2;
      int CX3 = CY3;
      float p = zoff + dy * y;
      for (int x = minx; x < maxx; x++) {
        if (CX1 > 0 && CX2 > 0 && CX3 > 0) {
          int point = x + off;
          float zval = p + dx * x;
          if (minz[point] > zval) {
            minz[point] = zval;
          }
          if (maxz[point] < zval) {
            maxz[point] = zval;
          }
        }
        CX1 -= FDY12;
        CX2 -= FDY23;
        CX3 -= FDY31;
      }
      CY1 += FDX12;
      CY2 += FDX23;
      CY3 += FDX31;
      off += w;
    }
  }

  public static void toZBuffMinMax(float[] minz, float[] maxz, int w, int h, TriData tri) {
    for (int i = 0; i < tri.myIndex.length; i += 3) {
      int p1 = tri.myIndex[i];
      int p2 = tri.myIndex[i + 1];
      int p3 = tri.myIndex[i + 2];
      triangleZBuffMinMax(minz, maxz, w, h, tri.myVert[p1], tri.myVert[p1 + 1],
                          tri.myVert[p1 + 2], tri.myVert[p2], tri.myVert[p2 + 1],
                          tri.myVert[p2 + 2], tri.myVert[p3], tri.myVert[p3 + 1],
                          tri.myVert[p3 + 2]);
    }
  }

  public static void toZBuff(float[] zbuff, int w, int h, TriData tri) {
    for (int i = 0; i < tri.myIndex.length; i += 3) {
      int p1 = tri.myIndex[i];
      int p2 = tri.myIndex[i + 1];
      int p3 = tri.myIndex[i + 2];
      triangleZBuffMin(zbuff, w, h, tri.myVert[p1], tri.myVert[p1 + 1],
                       tri.myVert[p1 + 2], tri.myVert[p2], tri.myVert[p2 + 1],
                       tri.myVert[p2 + 2], tri.myVert[p3], tri.myVert[p3 + 1],
                       tri.myVert[p3 + 2]);
    }
  }

  public static final int BLUE = 0x5599FF;
  public static final int GRAY = 0x101010;

  /**
   * CheckLayout3d entery point to convert tri data to an image
   *
   * @param zbuff   used to provide the z-buffer
   * @param rgb     the image is output here
   * @param w       the width of the image
   * @param h       the height of the image
   * @param tri     the data set it contains triangles indexes etc
   * @param texture the myTexture use to render the triangles with
   * @param tWidth  the width of the myTexture
   * @param tHeight the height of the myTexture
   * @param matrix  the matrix that define the transform (Helps simplify the myTexture mapping.
   */
  public static void toZBuff(float[] zbuff, int[] rgb, int w, int h, TriData tri, int[] texture,
                             int tWidth, int tHeight, double[] matrix) {
    for (int i = 0; i < tri.myIndex.length; i += 3) {
      int p1 = tri.myIndex[i];
      int p2 = tri.myIndex[i + 1];
      int p3 = tri.myIndex[i + 2];
      int type = tri.mySurfaceType[i / 3];
      if (type == 1) {
        render_perspectiveAffine(zbuff, rgb, w, h,
                                 tri.myVert[p1], tri.myVert[p1 + 1], tri.myVert[p1 + 2],
                                 tri.myVert[p2], tri.myVert[p2 + 1], tri.myVert[p2 + 2],
                                 tri.myVert[p3], tri.myVert[p3 + 1], tri.myVert[p3 + 2],
                                 tri.myTexture_uv[p1], tri.myTexture_uv[p1 + 1],
                                 tri.myTexture_uv[p2], tri.myTexture_uv[p2 + 1],
                                 tri.myTexture_uv[p3], tri.myTexture_uv[p3 + 1],
                                 texture, tWidth, tHeight, matrix
        );
      }
      else {
        flat(zbuff, rgb, w, h,
             tri.myVert[p1], tri.myVert[p1 + 1], tri.myVert[p1 + 2],
             tri.myVert[p2], tri.myVert[p2 + 1], tri.myVert[p2 + 2],
             tri.myVert[p3], tri.myVert[p3 + 1], tri.myVert[p3 + 2],

             (type == 0) ? BLUE : GRAY
        );
      }
    }
  }

  /**
   * Simple flat (defuse) shaded rendering
   *
   * @param zbuff used to provide the z-buffer
   * @param rgb   the image is output here
   * @param w     the width of the image
   * @param hthe  height of the image
   * @param tri   the data set it contains triangles indexes etc
   */
  public static void simple(float[] zbuff, int[] rgb, int w, int h, TriData tri) {

    for (int i = 0; i < tri.myIndex.length; i += 3) {
      int p1 = tri.myIndex[i];
      int p2 = tri.myIndex[i + 1];
      int p3 = tri.myIndex[i + 2];
      int type = (tri.mySurfaceType != null) ? tri.mySurfaceType[i / 3] : 0;
      flat(zbuff, rgb, w, h,
           tri.myVert[p1], tri.myVert[p1 + 1], tri.myVert[p1 + 2],
           tri.myVert[p2], tri.myVert[p2 + 1], tri.myVert[p2 + 2],
           tri.myVert[p3], tri.myVert[p3 + 1], tri.myVert[p3 + 2],

           (type == 0) ? BLUE : GRAY
      );
    }
  }

  /**
   * debugging support code
   */
  private static DecimalFormat df = new DecimalFormat("       #####0.000;             -####0.000");

  private static String toString(float... x) {
    String ret = null;
    for (int i = 0; i < x.length; i++) {
      float v = x[i];
      String s = df.format(v);
      if (ret == null) {
        ret = s.substring(s.length() - 9);
      }
      else {
        ret += "," + s.substring(s.length() - 9);
      }
    }
    return ret;
  }
}
