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
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;

import java.awt.*;

/**
 * Draw the background of a SceneComponent
 */
public class DrawComponentBackground extends DrawRegion {
  public static final int SUBDUED = 0;
  public static final int NORMAL = 1;
  public static final int OVER = 2;
  public static final int SELECTED = 3;

  private final int myMode;

  public DrawComponentBackground(String s) {
    String[] sp = s.split(",");
    int c = 0;
    c = super.parse(sp, c);
    myMode = Integer.parseInt(sp[c++]);
  }

  @Override
  public int getLevel() {
    return COMPONENT_LEVEL;
  }

  public DrawComponentBackground(@SwingCoordinate int x,
                                 @SwingCoordinate int y,
                                 @SwingCoordinate int width,
                                 @SwingCoordinate int height,
                                 int mode) {
    super(x, y, width, height);
    myMode = mode;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color[] colorBackground = {colorSet.getComponentBackground(), colorSet.getComponentBackground(),
      colorSet.getComponentHighlightedBackground(), colorSet.getComponentHighlightedBackground(),
      colorSet.getDragReceiverBackground()};
    if (colorSet.drawBackground()) {
      g.setColor(colorBackground[myMode]);
      g.fillRect(x, y, width, height);
    }
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + myMode;
  }

  public static void add(DisplayList list,
                         SceneContext sceneContext,
                         @AndroidDpCoordinate Rectangle rect,
                         int mode) {
    int l = sceneContext.getSwingXDip(rect.x);
    int t = sceneContext.getSwingYDip(rect.y);
    int w = sceneContext.getSwingDimensionDip(rect.width);
    int h = sceneContext.getSwingDimensionDip(rect.height);
    list.add(new DrawComponentBackground(l, t, w, h, mode));
  }
}
