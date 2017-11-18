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
 * Vertical Guideline
 */
public class DrawVerticalGuideline extends DrawRegion {

  @SwingCoordinate private final static int GAP = 40;

  @SwingCoordinate private int myBegin;
  @SwingCoordinate private int myEnd;
  private float myPercent;
  @SwingCoordinate private int myOriginX;
  @SwingCoordinate private int myOriginY;
  @SwingCoordinate private int myOriginWidth;
  private boolean myIsSelected;

  protected Font mFont = new Font("Helvetica", Font.PLAIN, 14);

  public DrawVerticalGuideline(String s) {
    super(s);
    myBegin = -1;
    myEnd = -1;
    myPercent = 0.5f;
  }

  public DrawVerticalGuideline(@SwingCoordinate int x,
                               @SwingCoordinate int y,
                               @SwingCoordinate int height,
                               @SwingCoordinate int originX,
                               @SwingCoordinate int originY,
                               @SwingCoordinate int originWidth,
                               int begin,
                               int end,
                               float percent,
                               boolean selected) {
    super(x, y, x, height);
    myBegin = begin;
    myEnd = end;
    myPercent = percent;
    myOriginX = originX;
    myOriginY = originY;
    myOriginWidth = originWidth;
    myIsSelected = selected;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Stroke stroke = g.getStroke();
    if (myIsSelected) {
      g.setColor(colorSet.getSelectedFrames());
    } else {
      g.setColor(colorSet.getFrames());
    }
    g.setStroke(DrawConnectionUtils.sDashedStroke);
    g.drawLine(x, y, x, y + height);
    g.setStroke(stroke);
    int gap = 48;
    if (myIsSelected) {
      if (myBegin != -1) {
        DrawConnectionUtils.drawHorizontalMarginIndicator(g, String.valueOf(myBegin), false, myOriginX, x, myOriginY + GAP);
      }
      else if (myEnd != -1) {
        DrawConnectionUtils.drawHorizontalMarginIndicator(g, String.valueOf(myEnd), false, x, myOriginX + myOriginWidth, myOriginY + GAP);
      }
      else {
        String percent = String.valueOf((int) (myPercent * 100)) + " %";
        g.setColor(colorSet.getFrames());
        DrawConnectionUtils.drawRoundRectText(g, mFont, colorSet.getText(), percent, x, y + gap);
      }
    }
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float bottom) {
    add(list, transform, left, top, bottom, -1, -1, -1, -1, -1, 1.0f, false);
  }

  public static void add(DisplayList list, SceneContext transform,
                         @AndroidDpCoordinate float left, @AndroidDpCoordinate float top, @AndroidDpCoordinate float bottom,
                         @AndroidDpCoordinate float originX, @AndroidDpCoordinate float originY, @AndroidDpCoordinate float originWidth,
                         int begin, int end, float percent, boolean selected) {
    int l = transform.getSwingXDip(left);
    int t = transform.getSwingYDip(top);
    int h = transform.getSwingDimensionDip(bottom - top);
    int ox = transform.getSwingXDip(originX);
    int oy = transform.getSwingYDip(originY);
    int ow = transform.getSwingDimensionDip(originWidth);
    list.add(new DrawVerticalGuideline(l, t, h, ox, oy, ow, begin, end, percent, selected));
  }
}
