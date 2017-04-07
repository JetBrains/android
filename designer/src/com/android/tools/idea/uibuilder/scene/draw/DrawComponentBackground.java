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
 * Draw the background of a SceneComponent
 */
public class DrawComponentBackground extends DrawRegion {
  public static final int SUBDUED = 0;
  public static final int NORMAL = 1;
  public static final int OVER = 2;
  public static final int SELECTED = 3;

  int myMode;

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

  public DrawComponentBackground(int x,
                                 int y,
                                 int width,
                                 int height,
                                 int mode) {
    super(x, y, width, height);
    myMode = mode;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color[] colorBackground = {colorSet.getComponentBackground(), colorSet.getComponentBackground(), colorSet.getComponentHighlightedBackground(), colorSet.getComponentHighlightedBackground()};
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
                         Rectangle rect,
                         int mode,
                         boolean hasHorizontalConstraints,
                         boolean hasVerticalConstraints) {
    int l = sceneContext.getSwingX(rect.x);
    int t = sceneContext.getSwingY(rect.y);
    int w = sceneContext.getSwingDimension(rect.width);
    int h = sceneContext.getSwingDimension(rect.height);
    list.add(new DrawComponentBackground(l, t, w, h, mode));
  }
}
