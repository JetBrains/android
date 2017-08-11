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
import com.android.tools.sherpa.drawing.ColorSet;

import java.awt.*;

/**
 *  Drawing of the guideline cycle
 */
public class DrawGuidelineCycle extends DrawRegion {
  public static final int BEGIN = 0;
  public static final int END = 1;
  public static final int PERCENT = 16;

  int myMode;
  boolean myIsHorizontal;
  boolean myIsSelected;

  protected Font mFont = new Font("Helvetica", Font.PLAIN, 14);

  @SwingCoordinate int[] xPoints = new int[3];
  @SwingCoordinate int[] yPoints = new int[3];

  @Override
  public int getLevel() {
    return POST_CLIP_LEVEL;
  }

  public DrawGuidelineCycle(String s) {
    super(s);
    String[] sp = s.split(",");
    int c = 0;
    c = super.parse(sp, c);
    myMode = Integer.parseInt(sp[c++]);
    myIsHorizontal = Boolean.parseBoolean(sp[c++]);
    myIsSelected = Boolean.parseBoolean(sp[c++]);
  }

  public DrawGuidelineCycle(boolean isHorizontal,
                            @SwingCoordinate int x,
                            @SwingCoordinate int y,
                            @SwingCoordinate int width,
                            @SwingCoordinate int height,
                            int mode,
                            boolean selected) {
    super(x, y, width, height);
    myIsHorizontal = isHorizontal;
    myMode = mode;
    myIsSelected = selected;
  }

  @Override
  public String serialize() {
    return this.getClass().getSimpleName() + "," + x + "," + y + "," + width + "," + height + "," + myMode + "," + myIsHorizontal + "," + myIsSelected;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    g.setColor(colorSet.getFrames());
    g.fillOval(x, y, width, height);
    if (myIsSelected) {
      g.setColor(colorSet.getSelectedFrames());
      g.setStroke(colorSet.getOutlineStroke());
    }
    g.drawOval(x, y, width, height);
    g.setColor(colorSet.getText());
    if (myMode == PERCENT) {
      String text = "%";
      g.setFont(mFont);
      FontMetrics fontMetrics = g.getFontMetrics();
      int tx = x + (width - fontMetrics.stringWidth(text))/2;
      int ty = y + (height / 2) + fontMetrics.getDescent();
      g.drawString(text, tx + 1, ty + 1);
    } else if (myMode == BEGIN) {
      int gap = 4;
      if (myIsHorizontal) {
        DrawConnectionUtils.getArrow(DrawConnection.DIR_BOTTOM, x + width / 2, y + height / 2 - gap, xPoints, yPoints);
      } else {
        DrawConnectionUtils.getArrow(DrawConnection.DIR_RIGHT, x + width / 2 - gap, y + height / 2, xPoints, yPoints);
      }
      g.fillPolygon(xPoints, yPoints, 3);
    } else {
      int gap = 4;
      if (myIsHorizontal) {
        DrawConnectionUtils.getArrow(DrawConnection.DIR_TOP, x + width / 2, y + height / 2 + gap, xPoints, yPoints);
      } else {
        DrawConnectionUtils.getArrow(DrawConnection.DIR_LEFT, x + width / 2 + gap, y + height / 2, xPoints, yPoints);
      }
      g.fillPolygon(xPoints, yPoints, 3);
    }
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         boolean isHorizontal, float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float bottom,
                         int mode,
                         boolean selected) {
    int l = transform.getSwingX(left);
    int t = transform.getSwingY(top);
    int w = transform.getSwingDimension(right - left);
    int h = transform.getSwingDimension(bottom - top);
    list.add(new DrawGuidelineCycle(isHorizontal, l, t, w, h, mode, selected));
  }
}
