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

import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils; // TODO: remove
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.sherpa.drawing.ColorSet;

import java.awt.*;

/**
 * Horizontal line
 */
public class DrawHorizontalLine extends DrawRegion {

  protected Font mFont = new Font("Helvetica", Font.PLAIN, 14);

  public DrawHorizontalLine(String s) {
    super(s);
  }
  public DrawHorizontalLine(int x,
                            int y,
                            int width) {
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

  public static void add(DisplayList list, SceneContext transform, float left, float top, float right) {
    add(list, transform, left, top, right, -1, -1, -1, -1, -1, 1.0f, false);
  }

  public static void add(DisplayList list, SceneContext transform, float left, float top, float right,
                         float originX, float originY, float originHeight,
                         int begin, int end, float percent, boolean selected) {
    int l = transform.getSwingX(left);
    int t = transform.getSwingY(top);
    int w = transform.getSwingDimension(right - left);
    list.add(new DrawHorizontalLine(l, t, w));
  }
}
