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
package com.android.tools.idea.uibuilder.handlers.linear.draw;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;

import java.awt.*;

/**
 * {@link DrawCommand} to draw a separator for LinearLayout
 */
public class DrawLinearSeparator extends DrawRegion {

  public static final int STROKE_SIZE = 2;
  private static final Stroke STROKE = new BasicStroke(STROKE_SIZE, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                                                       0, new float[]{8, 6}, 0);

  public DrawLinearSeparator(boolean layoutVertical,
                             @SwingCoordinate int x,
                             @SwingCoordinate int y,
                             @SwingCoordinate int length) {
    setLocation(x, y);
    if (layoutVertical) {
      setSize(length, 0);
    }
    else {
      setSize(0, length);
    }
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Stroke defStroke = g.getStroke();
    Color defColor = g.getColor();
    g.setStroke(STROKE);
    g.setColor(colorSet.getDragReceiverFrames());
    g.drawLine(x, y, x + width, y + height);

    g.setColor(defColor);
    g.setStroke(defStroke);
  }

  /**
   * @param layoutVertical The orientation of the parent linear layout
   */
  public static void add(DisplayList list, SceneContext context,
                         boolean layoutVertical,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float bottom) {

    float length = layoutVertical ? right - left : bottom - top;
    DrawLinearSeparator separator =
      new DrawLinearSeparator(layoutVertical,
                              context.getSwingXDip(left),
                              context.getSwingYDip(top),
                              context.getSwingDimensionDip(length));
    list.add(separator);
  }
}
