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
 * Generic Drawing of a selection Lasso
 */
public class DrawLasso extends DrawRegion {
  public static final int NORMAL = 0;
  public static final int OVER = 1;
  private static final int GAP = 16;

  public DrawLasso(String s) {
    super(s);
  }

  public DrawLasso(int x, int y, int width, int height) {
    super(x, y, width, height);
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color background = colorSet.getFrames();
    g.setColor(background);
    String valueWidth = String.valueOf(width);
    DrawConnectionUtils.drawHorizontalMarginIndicator(g, valueWidth, x, x + width, y - GAP);
    String valueHeight = String.valueOf(height);
    DrawConnectionUtils.drawVerticalMarginIndicator(g, valueHeight, x - GAP, y, y + height);
    g.setStroke(DrawConnectionUtils.sDashedStroke);
    g.drawRect(x, y, width, height);
  }

  public static void add(DisplayList list, SceneContext transform, float left, float top, float right, float bottom) {
    int l = transform.getSwingX(left);
    int t = transform.getSwingY(top);
    int w = transform.getSwingDimension(right - left);
    int h = transform.getSwingDimension(bottom - top);
    list.add(new DrawLasso(l, t, w, h));
  }
}
