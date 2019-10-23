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

import java.io.FileReader;
import java.io.LineNumberReader;
import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * The representation of the Triangle Data sets
 * It contains vertices indexes type to indicate what type of triangle to draw
 * texture for a single texture map
 * texture uv for mapping texture coordinates
 */
public class TriData {
  protected float[] myVert;
  protected float[] myTexture_uv;
  protected int[] myIndex;
  protected int[] mySurfaceType;

  int[] myTexture;
  int myTextureWidth;
  int myTextureHeight;

  /**
   * It is ok to create and then initialize
   */
  public TriData() {
  }

  public void bounds(float[] min, float[] max) {
    for (int i = 0; i < myVert.length; i += 3) {
      float x = myVert[i];
      float y = myVert[i + 1];
      float z = myVert[i + 2];
      if (min != null) {
        min[0] = Math.min(x, min[0]);
        min[1] = Math.min(x, min[1]);
        min[2] = Math.min(x, min[2]);
      }
      if (max != null) {
        max[0] = Math.max(x, max[0]);
        max[1] = Math.max(x, max[1]);
        max[2] = Math.max(x, max[2]);
      }
    }
  }

  public void print() {
    class F extends DecimalFormat {
      public F() {
        super("      ##0.000");
      }

      public String f(double number) {
        String ret = super.format(number);
        return ret.substring(ret.length() - 7);
      }
    }
    F df = new F();
    for (int i = 0; i < myVert.length; i += 3) {

      System.out.print(i / 3 + "[ " + df.f(myVert[i]));
      System.out.print(", " + df.f(myVert[i + 1]));
      System.out.println(", " + df.f(myVert[i + 2]) + "]");
    }
  }

  public TriData(TriData clone) {
    if (clone.myTexture_uv != null) {
      myTexture_uv = Arrays.copyOf(clone.myTexture_uv, clone.myTexture_uv.length);
    }
    mySurfaceType = clone.mySurfaceType;
    myVert = Arrays.copyOf(clone.myVert, clone.myVert.length);
    myIndex = Arrays.copyOf(clone.myIndex, clone.myIndex.length);
  }

  public void scale(float[] s) {
    for (int i = 0; i < myVert.length; i += 3) {
      myVert[i] *= s[0];
      myVert[i + 1] *= s[1];
      myVert[i + 2] *= s[2];
    }
  }

  public void scale(double[] s) {
    for (int i = 0; i < myVert.length; i += 3) {
      myVert[i] *= s[0];
      myVert[i + 1] *= s[1];
      myVert[i + 2] *= s[2];
    }
  }

  public void transform(Matrix m) {
    for (int i = 0; i < myVert.length; i += 3) {
      m.mult3(myVert, i, myVert, i);
    }
  }

  public void transform(Matrix m, TriData out) {

    for (int i = 0; i < myVert.length; i += 3) {
      m.mult3(myVert, i, out.myVert, i);
    }
  }

  public void transformP(Matrix m, TriData out, float cx, float cy, float scrDist) {

    for (int i = 0; i < myVert.length; i += 3) {
      m.mult3(myVert, i, out.myVert, i);
      out.myVert[i] = cx + (out.myVert[i] - cx) * scrDist / out.myVert[i + 2];
      out.myVert[i + 1] = cy + (out.myVert[i + 1] - cy) * scrDist / out.myVert[i + 2];
    }
  }

  public void ptransform(ViewMatrix vm, Matrix m, TriData out) {
    float dist = (float)vm.screenDistance();
    float cx = (float)vm.myScreenDim[0] / 2;
    float cy = (float)vm.myScreenDim[1] / 2;
    for (int i = 0; i < myVert.length; i += 3) {
      m.mult3(myVert, i, out.myVert, i);
      out.myVert[i] = cx + (out.myVert[i] - cx) * dist / out.myVert[i + 2];
      out.myVert[i + 1] = cy + (out.myVert[i + 1] - cy) * dist / out.myVert[i + 2];
    }
  }

  /**
   * Can load data sets found at
   * http://www.cs.umd.edu/class/fall2010/cmsc741/datasets.html
   *
   * num_verts v_0_x v_0_y v_0_z v_1_x v_1_y v_1_z ... v_n_x v_n_y v_n_z num_tris
   * tri_0_1 tri_0_2 tri_0_3 tri_1_1 tri_1_2 tri_1_3 ... tri_m_1 tri_m_2 tri_m_3
   *
   * @param fileName
   */
  public void read(String fileName) {
    try {
      FileReader fr = new FileReader(fileName);
      LineNumberReader lnr = new LineNumberReader(fr);
      int num_verts = Integer.parseInt(lnr.readLine());
      System.out.println("verts =" + num_verts);
      myVert = new float[num_verts * 3];
      int k = 0;
      for (int i = 0; i < num_verts; i++) {
        String[] s = lnr.readLine().split("\\s");

        for (int j = 0; j < s.length; j++) {
          myVert[k++] = Float.parseFloat(s[j]);
        }
      }
      int num_tri = Integer.parseInt(lnr.readLine());
      System.out.println("tri =" + num_tri);
      myIndex = new int[3 * num_tri];
      k = 0;
      for (int i = 0; i < num_tri; i++) {
        String[] s = lnr.readLine().split("\\s");
        for (int j = 0; j < s.length; j++) {
          myIndex[k++] = Integer.parseInt(s[j]);
        }
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
