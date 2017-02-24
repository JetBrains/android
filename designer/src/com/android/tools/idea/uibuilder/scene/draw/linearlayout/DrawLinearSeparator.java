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
package com.android.tools.idea.uibuilder.scene.draw.linearlayout;

import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawCommand;
import com.android.tools.idea.uibuilder.scene.draw.DrawRegion;
import com.android.tools.sherpa.drawing.ColorSet;

import java.awt.*;

/**
 * {@link DrawCommand} to draw a separator for LinearLayout
 */
public class DrawLinearSeparator extends DrawRegion {

  public static final int STATE_DEFAULT = 0;
  public static final int STATE_HIGHLIGHT = 1;
  public static final int STATE_SELECTED = 2;

  private static final int STROKE_SIZE = 1;

  private static Stroke STROKE = new BasicStroke(STROKE_SIZE,
                                                 BasicStroke.CAP_BUTT,
                                                 BasicStroke.JOIN_BEVEL,
                                                 0, new float[]{2, 3},
                                                 0);
  private static Stroke STROKE_BOLD = new BasicStroke(STROKE_SIZE * 2,
                                                      BasicStroke.CAP_BUTT,
                                                      BasicStroke.JOIN_BEVEL,
                                                      0, new float[]{4, 6},
                                                      0);


  private final int myState;

  public DrawLinearSeparator(boolean vertical,
                             int state,
                             @SwingCoordinate int x,
                             @SwingCoordinate int y,
                             @SwingCoordinate int length) {
    setLocation(x, y);
    if (vertical) {
      setSize(STROKE_SIZE, length);
    }
    else {
      setSize(length, STROKE_SIZE);
    }
    myState = state;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Stroke defStroke = g.getStroke();
    Color defColor = g.getColor();

    Color color;

    g.setStroke(STROKE);
    switch (myState) {
      case STATE_HIGHLIGHT:
        color = colorSet.getSelectedFrames();
        g.setStroke(STROKE_BOLD);
        break;
      case STATE_SELECTED:
        color = colorSet.getSelectedFrames();
        break;
      default:
        color = colorSet.getFrames();
    }
    g.setColor(color);
    g.drawLine(x, y, x + width, y + height);

    g.setColor(defColor);
    g.setStroke(defStroke);
  }

  public static void add(DisplayList list, SceneContext context,
                         boolean vertical,
                         int state,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float bottom) {

    float length = vertical ? bottom - top : right - left;
    DrawLinearSeparator separator =
      new DrawLinearSeparator(vertical,
                              state,
                              context.getSwingX(left),
                              context.getSwingY(top),
                              context.getSwingDimension(length));
    list.add(separator);
  }
}
