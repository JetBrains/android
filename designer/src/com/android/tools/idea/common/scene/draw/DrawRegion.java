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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;

import java.awt.*;

/**
 * base class for regions based on rectangles
 */
public class DrawRegion extends Rectangle implements DrawCommand {

  /**
   * distance from rectangle to point
   * 0 inside the rectangle
   * @param mx
   * @param my
   * @return
   */

  public static  boolean inside(int px, int py, int x, int y, int width, int height) {
    int w = width;
    int h = height;
    if ((w | h) < 0) {
      return false;
    }
    if (px < x || py < y) {
      return false;
    }
    w += x;
    h += y;
    //    overflow || intersect
    return ((w < x || w > px) &&
            (h < y || h > py));
  }

  public static float distance(int mx,int my, int x, int y, int width, int height) {

    if (inside(mx, my, x,  y,  width,  height)) {
      return 0;
    }
    else {
      if (mx > x && mx < x + width) {
        return Math.min(Math.abs(my - y), Math.abs(my - (y + height)));
      }
      else if (my > y && my < y + height) {
        return Math.min(Math.abs(mx - x), Math.abs(mx - (x + width)));
      }
      else if (mx <= x && my <= y) {
        float dx = mx - x;
        float dy = my - y;
        return (float)Math.hypot(dx, dy);
      }
      else if (mx <= x && my >= y + height) {
        float dx = mx - x;
        float dy = my - (y + height);
        return (float)Math.hypot(dx, dy);
      }
      else if (mx >= x + width && my <= y) {
        float dx = mx - (x + width);
        float dy = my - y;
        return(float)Math.hypot(dx, dy);
      }
      else if (mx >= x + width && my >= y + height) {
        float dx = mx - (x + width);
        float dy = my - (y + height);
        return (float)Math.hypot(dx, dy);
      }
    }
    return 0;
  }

  @Override
  public String serialize() {
    return this.getClass().getSimpleName()+"," + x + "," + y + "," + width + "," + height;
  }

  public DrawRegion() {
  }

  @Override
  public int getLevel() {
    return TARGET_LEVEL;
  }

  public DrawRegion(String s) {
    String[] sp = s.split(",");
    parse(sp, 0);
  }

  protected int parse(String[] sp, int c) {
    x = Integer.parseInt(sp[c++]);
    y = Integer.parseInt(sp[c++]);
    width = Integer.parseInt(sp[c++]);
    height = Integer.parseInt(sp[c++]);
    return c;
  }

  public DrawRegion(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width, @SwingCoordinate int height) {
    super(x, y, width, height);
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    g.drawRect(x, y, width, height);
  }

}

