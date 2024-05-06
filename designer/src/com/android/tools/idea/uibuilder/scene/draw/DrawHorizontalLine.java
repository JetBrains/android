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
package com.android.tools.idea.uibuilder.scene.draw;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * Horizontal line
 */
public class DrawHorizontalLine extends DrawRegion {

  protected Font mFont = new Font("Helvetica", Font.PLAIN, 14);

  public DrawHorizontalLine(String s) {
    super(s);
  }
  public DrawHorizontalLine(@SwingCoordinate int x,
                            @SwingCoordinate int y,
                            @SwingCoordinate int width) {
    //noinspection SuspiciousNameCombination
    super(x, y, width, x);
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    g.setColor(colorSet.getFrames());
    Stroke stroke = g.getStroke();
    g.setStroke(DrawConnectionUtils.sDashedStroke);
    g.drawLine(x, y, x + width, y);
    g.setStroke(stroke);
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right) {
    int l = transform.getSwingXDip(left);
    int t = transform.getSwingYDip(top);
    int w = transform.getSwingDimensionDip(right - left);
    list.add(new DrawHorizontalLine(l, t, w));
  }
}
