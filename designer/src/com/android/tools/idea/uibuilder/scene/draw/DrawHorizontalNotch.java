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

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils; // TODO: remove
import com.android.tools.sherpa.drawing.ColorSet;

import java.awt.*;

/**
 * Horizontal notch
 */
public class DrawHorizontalNotch extends DrawRegion {

  public DrawHorizontalNotch(String s) {
    super(s);
  }
  public DrawHorizontalNotch(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width) {
    //noinspection SuspiciousNameCombination
    super(x, y, width, x);
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
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right) {
    int l = transform.getSwingX(left);
    int t = transform.getSwingY(top);
    int w = transform.getSwingDimension(right - left);
    list.add(new DrawHorizontalNotch(l, t, w));
  }
}
