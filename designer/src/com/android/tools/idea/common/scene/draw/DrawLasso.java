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
package com.android.tools.idea.common.scene.draw;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils; // TODO: remove
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;

import java.awt.*;

/**
 * Generic Drawing of a selection Lasso
 */
public class DrawLasso extends DrawRegion {
  public static final int NORMAL = 0;
  public static final int OVER = 1;
  private static final int GAP = 16;
  private boolean myShowMargins;

  public DrawLasso(String s) {
    super(s);
  }

  public DrawLasso(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width, @SwingCoordinate int height, boolean showMargins) {
    super(x, y, width, height);
    myShowMargins = showMargins;
  }

  @Override
  protected int parse(String[] sp, int c) {
    myShowMargins = Boolean.parseBoolean(sp[c++]);
    return c;
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + myShowMargins;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color borderColor = colorSet.getLassoSelectionBorder();
    g.setColor(borderColor);
    if(myShowMargins) {
      String valueWidth = String.valueOf((int)(sceneContext.pxToDp(width) / sceneContext.getScale()));
      DrawConnectionUtils.drawHorizontalMarginIndicator(g, valueWidth, false, x, x + width, y - GAP);
      String valueHeight = String.valueOf((int)(sceneContext.pxToDp(height) / sceneContext.getScale()));
      DrawConnectionUtils.drawVerticalMarginIndicator(g, valueHeight, false, x - GAP, y, y + height);
    }
    g.drawRect(x, y, width, height);
    Color fillColor = colorSet.getLassoSelectionFill();
    g.setColor(fillColor);
    g.fillRect(x, y, width, height);
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float bottom,
                         boolean showMargins) {
    int l = transform.getSwingXDip(left);
    int t = transform.getSwingYDip(top);
    int w = transform.getSwingDimensionDip(right - left);
    int h = transform.getSwingDimensionDip(bottom - top);
    list.add(new DrawLasso(l, t, w, h, showMargins));
  }
}
