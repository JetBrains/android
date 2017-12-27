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
import com.android.tools.sherpa.drawing.ColorSet;

import java.awt.*;

/**
 * Draw the background of a SceneComponent
 */
public class DrawComponentBackground extends DrawRegion {
  public static final int SUBDUED = 0;
  public static final int NORMAL = 1;
  public static final int OVER = 2;
  public static final int SELECTED = 3;
  public static final int ARC_SIZE = 20;

  private final int myMode;
  private final boolean myRounded;

  public DrawComponentBackground(String s) {
    String[] sp = s.split(",");
    int c = 0;
    c = super.parse(sp, c);
    myMode = Integer.parseInt(sp[c++]);
    myRounded = Boolean.parseBoolean(sp[c]);
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
    this(x, y, width, height, mode, false);
  }

  public DrawComponentBackground(@SwingCoordinate int x,
                                 @SwingCoordinate int y,
                                 @SwingCoordinate int width,
                                 @SwingCoordinate int height,
                                 int mode, boolean rounded) {
    super(x, y, width, height);
    myMode = mode;
    myRounded = rounded;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color[] colorBackground = {colorSet.getComponentBackground(), colorSet.getComponentBackground(),
      colorSet.getComponentHighlightedBackground(), colorSet.getComponentHighlightedBackground(),
      colorSet.getDragReceiverBackground()};
    if (colorSet.drawBackground()) {
      g.setColor(colorBackground[myMode]);
      if (myRounded) {
        g.fillRoundRect(x, y, width, height, ARC_SIZE, ARC_SIZE);
      }
      else {
        g.fillRect(x, y, width, height);
      }
    }
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + myMode + "," + myRounded;
  }

  public static void add(DisplayList list,
                         SceneContext sceneContext,
                         @AndroidDpCoordinate Rectangle rect,
                         int mode) {
    add(list, sceneContext, rect, mode, false);
  }

  public static void add(DisplayList list,
                         SceneContext sceneContext,
                         @AndroidDpCoordinate Rectangle rect,
                         int mode, boolean rounded) {
    int l = sceneContext.getSwingX(rect.x);
    int t = sceneContext.getSwingY(rect.y);
    int w = sceneContext.getSwingDimension(rect.width);
    int h = sceneContext.getSwingDimension(rect.height);
    list.add(new DrawComponentBackground(l, t, w, h, mode, rounded));
  }
}
