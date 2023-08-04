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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.intellij.util.ui.JBUI;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Draw Anchors
 */
public class DrawResize extends DrawRegion {
  public static final int NORMAL = 0;
  public static final int OVER = 1;
  public static final int SIZE = JBUI.scale(8);

  int myMode;

  public DrawResize(String s) {
    String[] sp = s.split(",");
    int c = 0;
    c = super.parse(sp, c);
    myMode = Integer.parseInt(sp[c++]);
  }

  public DrawResize(@SwingCoordinate int x, @SwingCoordinate int y, int mode) {
    super(x - SIZE / 2, y - SIZE / 2, SIZE, SIZE);
    myMode = mode;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color background = colorSet.getSelectedFrames();
    Color borderColor = colorSet.getComponentObligatoryBackground();
    g.setColor(background);
    g.fillRect(x, y, width, height);
    //noinspection UseJBColor
    g.setColor(borderColor);
    g.drawRect(x, y, width, height);
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + myMode;
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         int mode) {
    int l = transform.getSwingXDip(left);
    int t = transform.getSwingYDip(top);
    list.add(new DrawResize(l, t, mode));
  }
}
