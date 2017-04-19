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

import com.intellij.util.ui.UIUtil;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * A Layout is a triangle data set built from a nested collection of views and an Image
 */
public class Layout extends TriData {
  BufferedImage mImage;
  public static final int CUBE_VERT_LENGTH = 8 * 3;
  public static final int CUBE_INDEX_LENGTH = 12 * 3;
  public static final int TRIANGLES_PER_CUBE = 12;

  /**
   * This represent a rect on the screen
   */
  public static class View extends Rectangle2D.Float {
    View parent;
    View child;
    View sister;
    public static final float DEPTH = 25;

    public View(float x, float y, float width, float height) {
      super(x, y, width, height);
    }

    public View addChild(View... v) {
      for (int i = 0; i < v.length; i++) {
        View view = v[i];
        view.parent = this;
        if (child == null) {
          child = view;
        }
        else {
          child.addSister(view);
        }
      }

      return this;
    }

    public View addSister(View v) {
      if (sister == null) {
        v.parent = parent;
        sister = v;
      }
      else {
        sister.addSister(v);
      }
      return this;
    }

    float getDepth() {
      float d = DEPTH;
      if (sister != null) {
        d = Math.max(sister.getDepth(), d);
      }
      if (child != null) {
        d = Math.max(DEPTH + child.getDepth(), d);
      }
      return d;
    }

    int countCubes() {
      int count = 1;
      if (sister != null) {
        count += sister.countCubes();
      }
      if (child != null) {
        for (View c = child; c != null; c = c.sister) {
          count++;
        }
      }
      if (child != null) {
        count += child.countCubes();
      }
      return count;
    }

    int count() {
      int count = 1;
      if (sister != null) {
        count += sister.count();
      }
      if (child != null) {
        count += child.count();
      }
      return count;
    }

    public void fill(float[] vertices, float[] texture_uv, int voff, int[] index, int[] stype,
                     int ioff, float z, boolean blue) {
      float d = blue ? 1 : DEPTH;
      float[] v = new float[]{
        x, y, z,
        x + width, y, z,
        x + width, y + height, z,
        x, y + height, z,
        x, y, z - d,
        x + width, y, z - d,
        x + width, y + height, z - d,
        x, y + height, z - d,
      };
      for (int i = 0; i < v.length; i++) {
        texture_uv[i + voff] = vertices[i + voff] = v[i];
        if (i % 3 == 2) {
          texture_uv[i + voff] = java.lang.Float.NaN;
        }
      }

      int[] ind = new int[]{
        2, 1, 0,
        0, 3, 2,
        7, 4, 5,
        5, 6, 7,
        1, 2, 6,
        6, 5, 1,
        4, 7, 3,
        3, 0, 4,
        2, 3, 7,
        7, 6, 2,
        0, 1, 5,
        5, 4, 0
      };
      int[] type = {0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,};

      for (int i = 0; i < ind.length; i++) {
        index[i + ioff] = ind[i] * 3 + voff;
        stype[(i + ioff) / 3] = blue ? 2 : type[i / 3];
      }
    }

    int layout(float[] vertices,
               float[] texture_uv,
               int voff,
               int[] index, int[] stype,
               int ioff, float z) {
      int count = 1;
      fill(vertices, texture_uv, voff, index, stype, ioff, z, false);
      voff += CUBE_VERT_LENGTH;
      ioff += CUBE_INDEX_LENGTH;
      if (parent != null) {
        fill(vertices, texture_uv, voff, index, stype, ioff, z + DEPTH, true);
        voff += CUBE_VERT_LENGTH;
        ioff += CUBE_INDEX_LENGTH;
        count++;
      }
      if (sister != null) {
        int sisters = sister.layout(vertices, texture_uv, voff, index, stype, ioff, z);
        count += sisters;
        voff += sisters * CUBE_VERT_LENGTH;
        ioff += sisters * CUBE_INDEX_LENGTH;
      }
      if (child != null) {
        count += child.layout(vertices, texture_uv, voff, index, stype, ioff, z - DEPTH * 2);
      }
      return count;
    }
  }

  public Layout(BufferedImage img, View view) {
    setUp(img, view);
  }

  public void setUp(BufferedImage img, View view) {
    int count = view.countCubes();
    myVert = new float[count * CUBE_VERT_LENGTH];
    myTexture_uv = new float[count * CUBE_VERT_LENGTH];

    myIndex = new int[count * CUBE_INDEX_LENGTH];
    mySurfaceType = new int[count * TRIANGLES_PER_CUBE];
    view.layout(myVert, myTexture_uv, 0, myIndex, mySurfaceType, 0, 0.f);

    if (img != null && img.getType() != BufferedImage.TYPE_INT_ARGB) {
      BufferedImage tmp;
      tmp = UIUtil.createImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
      tmp.createGraphics().drawImage(img, 0, 0, null);
      img = tmp;
    }
    mImage = img;
    myTexture = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
    myTextureWidth = img.getWidth();
    myTextureHeight = img.getHeight();
  }
}