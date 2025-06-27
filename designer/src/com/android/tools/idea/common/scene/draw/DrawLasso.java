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

import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import java.awt.Graphics2D;

/**
 * Generic Drawing of a selection Lasso
 */
public class DrawLasso extends DrawRegion {

  // TODO: Fixed the mouse position of SceneContext when using Design + Blueprint mode so we can get rid of these two properties.
  @SwingCoordinate private int myMouseX;
  @SwingCoordinate private int myMouseY;
  private boolean myShowSize;

  public DrawLasso(String s) {
    super(s);
  }

  public DrawLasso(@SwingCoordinate int x,
                   @SwingCoordinate int y,
                   @SwingCoordinate int width,
                   @SwingCoordinate int height,
                   @SwingCoordinate int mouseX,
                   @SwingCoordinate int mouseY,
                   boolean showSize) {
    super(x, y, width, height);
    myMouseX = mouseX;
    myMouseY = mouseY;
    myShowSize = showSize;
  }

  @Override
  protected int parse(String[] sp, int c) {
    myMouseX = Integer.parseInt(sp[c++]);
    myMouseY = Integer.parseInt(sp[c++]);
    myShowSize = Boolean.parseBoolean(sp[c++]);
    return c;
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + myMouseX + "," + myMouseY + "," + myShowSize;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    int dpWidth = (int)(sceneContext.pxToDp(width) / sceneContext.getScale());
    int dpHeight = (int)(sceneContext.pxToDp(height) / sceneContext.getScale());
    ColorSet colorSet = sceneContext.getColorSet();
    DrawLassoUtil.drawLasso(g, colorSet, x, y, width, height, myMouseX, myMouseY, dpWidth, dpHeight, myShowSize);
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float bottom,
                         @AndroidDpCoordinate float mouseX,
                         @AndroidDpCoordinate float mouseY,
                         boolean showSize) {
    int l = transform.getSwingXDip(left);
    int t = transform.getSwingYDip(top);
    int w = transform.getSwingDimensionDip(right - left);
    int h = transform.getSwingDimensionDip(bottom - top);
    int swingMouseX = transform.getSwingXDip(mouseX);
    int swingMouseY = transform.getSwingYDip(mouseY);
    list.add(new DrawLasso(l, t, w, h, swingMouseX, swingMouseY, showSize));
  }
}
