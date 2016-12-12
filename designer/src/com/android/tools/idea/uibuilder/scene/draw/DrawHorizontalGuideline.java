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
package com.android.tools.idea.uibuilder.scene.draw;

import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.sherpa.drawing.ColorSet;

import java.awt.*;

/**
 * Horizontal Guideline
 */
public class DrawHorizontalGuideline extends DrawRegion {

  private final static int GAP = 40;

  private int myBegin;
  private int myEnd;
  private float myPercent;
  private int myOriginX;
  private int myOriginY;
  private int myOriginHeight;

  public DrawHorizontalGuideline(String s) {
    super(s);
  }
  public DrawHorizontalGuideline(int x, int y, int width, int originX, int originY, int originHeight, int begin, int end, float percent) {
    super(x, y, width, x);
    myBegin = begin;
    myEnd = end;
    myPercent = percent;
    myOriginX = originX;
    myOriginY = originY;
    myOriginHeight = originHeight;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color background = colorSet.getFrames();
    g.setColor(background);
    Stroke stroke = g.getStroke();
    g.setStroke(DrawConnectionUtils.sDashedStroke);
    g.drawLine(x, y, x + width, y);
    g.setStroke(stroke);
    if (myBegin != -1) {
      DrawConnectionUtils.drawVerticalMarginIndicator(g, String.valueOf(myBegin), x + GAP, myOriginY, y);
    } else if (myEnd != -1) {
      DrawConnectionUtils.drawVerticalMarginIndicator(g, String.valueOf(myEnd), x + GAP, y, myOriginY+myOriginHeight);
    } else {

    }
  }

  public static void add(DisplayList list, SceneContext transform, float left, float top, float right,
                         float originX, float originY, float originHeight,
                         int begin, int end, float percent) {
    int l = transform.getSwingX(left);
    int t = transform.getSwingY(top);
    int w = transform.getSwingDimension(right - left);
    int ox = transform.getSwingX(originX);
    int oy = transform.getSwingY(originY);
    int oh = transform.getSwingDimension(originHeight);
    list.add(new DrawHorizontalGuideline(l, t, w, ox, oy, oh, begin, end, percent));
  }
}
