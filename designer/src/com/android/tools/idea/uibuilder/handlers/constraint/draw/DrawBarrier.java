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
public class DrawBarrier extends DrawRegion {
  @SwingCoordinate private final static int GAP = 12;
  private int myDirection;
  private boolean myIsSelected;
  public static final int TOP = 1;
  public static final int BOTTOM = 2;
  public static final int LEFT = 3;
  public static final int RIGHT = 4;

  @Override
  public String serialize() {
    return super.serialize() + "," + myDirection + "," + myIsSelected;
  }

  public DrawBarrier(String s) {
    super(s);
  }

  @Override
  protected int parse(String[] sp, int c) {
    c = super.parse(sp, c);
    myDirection = Integer.parseInt(sp[c++]);
    myIsSelected = Boolean.parseBoolean(sp[c++]);
    return c;
  }

  public DrawBarrier(@SwingCoordinate int x,
                     @SwingCoordinate int y,
                     @SwingCoordinate int size,
                     int direction,
                     boolean selected) {
    super(x, y, (direction == TOP || direction == BOTTOM) ? size : 1,
          (direction == LEFT || direction == RIGHT) ? size : 1);
    myDirection = direction;
    myIsSelected = selected;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color solid = (myIsSelected) ? colorSet.getSelectedFrames() : colorSet.getFrames();
    int baseRgb = solid.getRGB();
    int darkRgb = solid.darker().darker().getRGB();
    Color transparent1 = new Color((darkRgb & 0xFFFFFF)|0x8F000000, true);
    Color transparent2 = new Color(baseRgb & 0xFFFFFF, true);
    @SwingCoordinate int dx = 0, dy = 0;
    @SwingCoordinate int w = 0;
    @SwingCoordinate int h = 0;
    switch (myDirection) {
      case TOP:
        dy = -GAP;
        h = GAP;
        break;
      case BOTTOM:
        dy = GAP;
        h = GAP;
        break;
      case LEFT:
        dx = -GAP;
        w = GAP;
        break;
      case RIGHT:
        dx = GAP;
        w = GAP;
        break;
    }
    g.setColor(solid);
    Stroke previous = g.getStroke();
    g.setStroke(DrawConnectionUtils.sDashedStroke);
    g.drawLine(x, y, x + width, y + height);

    Paint p = new GradientPaint(x - dx, y - dy, transparent1, x + dx, y + dy, transparent2);
    g.setPaint(p);
    g.fillRect(x + ((dx < 0) ? -GAP : 0), y + ((dy < 0) ? -GAP : 0), width + w, height + h);
    g.setStroke(previous);
  }

  public static void add(DisplayList list, SceneContext transform,
                         @AndroidDpCoordinate float left, @AndroidDpCoordinate float top, @AndroidDpCoordinate float size,
                         int direction, boolean selected) {
    int l = transform.getSwingXDip(left);
    int t = transform.getSwingYDip(top);
    int pixSze = transform.getSwingDimensionDip(size);
    list.add(new DrawBarrier(l, t, pixSze, direction, selected));
  }
}
