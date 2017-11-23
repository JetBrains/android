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
package com.android.tools.idea.uibuilder.handlers.constraint.draw;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;

import java.awt.*;

/**
 * Horizontal Guideline
 */
public class DrawHorizontalGuideline extends DrawRegion {

  @SwingCoordinate private final static int GAP = 40;

  private int myBegin;
  private int myEnd;
  private float myPercent;
  @SwingCoordinate private int myOriginX;
  @SwingCoordinate private int myOriginY;
  @SwingCoordinate private int myOriginHeight;
  private boolean myIsSelected;

  protected Font mFont = new Font("Helvetica", Font.PLAIN, 14);

  public DrawHorizontalGuideline(String s) {
    super(s);
  }
  public DrawHorizontalGuideline(@SwingCoordinate int x,
                                 @SwingCoordinate int y,
                                 @SwingCoordinate int width,
                                 @SwingCoordinate int originX,
                                 @SwingCoordinate int originY,
                                 @SwingCoordinate int originHeight,
                                 int begin,
                                 int end,
                                 float percent,
                                 boolean selected) {
    //noinspection SuspiciousNameCombination
    super(x, y, width, x);
    myBegin = begin;
    myEnd = end;
    myPercent = percent;
    myOriginX = originX;
    myOriginY = originY;
    myOriginHeight = originHeight;
    myIsSelected = selected;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    if (myIsSelected) {
      g.setColor(colorSet.getSelectedFrames());
    } else {
      g.setColor(colorSet.getFrames());
    }
    Stroke stroke = g.getStroke();
    g.setStroke(DrawConnectionUtils.sDashedStroke);
    g.drawLine(x, y, x + width, y);
    g.setStroke(stroke);
    int gap = 48;
    if (myIsSelected) {
      if (myBegin != -1) {
        DrawConnectionUtils.drawVerticalMarginIndicator(g, String.valueOf(myBegin), false, x + GAP, myOriginY, y);
      }
      else if (myEnd != -1) {
        DrawConnectionUtils.drawVerticalMarginIndicator(g, String.valueOf(myEnd), false, x + GAP, y, myOriginY + myOriginHeight);
      }
      else {
        String percent = String.valueOf((int) (myPercent * 100)) + " %";
        g.setColor(colorSet.getFrames());
        DrawConnectionUtils.drawRoundRectText(g, mFont, colorSet.getText(), percent, x + gap, y);
      }
    }
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right) {
    add(list, transform, left, top, right, -1, -1, -1, -1, -1, 1.0f, false);
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float originX,
                         @AndroidDpCoordinate float originY,
                         @AndroidDpCoordinate float originHeight,
                         int begin,
                         int end,
                         float percent,
                         boolean selected) {
    int l = transform.getSwingXDip(left);
    int t = transform.getSwingYDip(top);
    int w = transform.getSwingDimensionDip(right - left);
    int ox = transform.getSwingXDip(originX);
    int oy = transform.getSwingYDip(originY);
    int oh = transform.getSwingDimensionDip(originHeight);
    list.add(new DrawHorizontalGuideline(l, t, w, ox, oy, oh, begin, end, percent, selected));
  }
}
