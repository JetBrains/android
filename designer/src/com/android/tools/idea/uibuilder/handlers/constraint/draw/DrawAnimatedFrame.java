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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;

import java.awt.*;

/**
 * Draws a twinkling around a frame (used during connection delete)
 */
public class DrawAnimatedFrame extends DrawRegion {
  private int mDirection;
  private int[] mXPoints = new int[6];
  private int[] mYPoints = new int[6];
  private static final Stroke myBasicStroke = new BasicStroke(2f);

  private static final Stroke myAnimationStroke =
    new BasicStroke(2, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1, new float[]{10, 10}, 0);
  private static final int REPAT_MS = 1000;
  private static final int PATERN_LENGTH = 20;

  public DrawAnimatedFrame(String s) {
    String[] sp = s.split(",");
    int c = 0;
    c = super.parse(sp, c);
    mDirection = Integer.parseInt(sp[c]);
  }

  @Override
  public int getLevel() {
    return TOP_LEVEL;
  }

  public DrawAnimatedFrame(@SwingCoordinate int x,
                           @SwingCoordinate int y,
                           @SwingCoordinate int width,
                           @SwingCoordinate int height, int direction
  ) {
    super(x, y, width, height);
    mDirection = direction;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    sceneContext.repaint();
    Stroke previousStroke = g.getStroke();
    Color previousColor = g.getColor();
    int shift = (int)(sceneContext.getTime() % REPAT_MS);
    shift /= REPAT_MS / PATERN_LENGTH;

    switch (mDirection) {
      case DrawConnection.DIR_LEFT:
        mXPoints[0] = x;
        mYPoints[0] = y + height / 2 - Math.min(shift, height / 2);
        mXPoints[1] = x;
        mYPoints[1] = y;
        mXPoints[2] = x + width;
        mYPoints[2] = y;
        mXPoints[3] = x + width;
        mYPoints[3] = y + height;
        mXPoints[4] = x;
        mYPoints[4] = y + height;
        mXPoints[5] = x;
        mYPoints[5] = y + height / 2;
        break;
      case DrawConnection.DIR_RIGHT:
        mXPoints[0] = x + width;
        mYPoints[0] = y + height / 2 + Math.min(shift, height / 2);
        mXPoints[1] = x + width;
        mYPoints[1] = y + height;
        mXPoints[2] = x;
        mYPoints[2] = y + height;
        mXPoints[3] = x;
        mYPoints[3] = y;
        mXPoints[4] = x + width;
        mYPoints[4] = y;
        mXPoints[5] = x + width;
        mYPoints[5] = y + height / 2;
        break;
      case DrawConnection.DIR_TOP:
        mXPoints[0] = x + width / 2 + Math.min(shift, width / 2);
        mYPoints[0] = y;
        mXPoints[1] = x + width;
        mYPoints[1] = y;
        mXPoints[2] = x + width;
        mYPoints[2] = y + height;
        mXPoints[3] = x;
        mYPoints[3] = y + height;
        mXPoints[4] = x;
        mYPoints[4] = y;
        mXPoints[5] = x + width / 2;
        mYPoints[5] = y;
        break;
      case DrawConnection.DIR_BOTTOM:
        mXPoints[0] = x + width / 2 - Math.min(shift, width / 2);
        mYPoints[0] = y + height;
        mXPoints[1] = x;
        mYPoints[1] = y + height;
        mXPoints[2] = x;
        mYPoints[2] = y;
        mXPoints[3] = x + width;
        mYPoints[3] = y;
        mXPoints[4] = x + width;
        mYPoints[4] = y + height;
        mXPoints[5] = x + width / 2;
        mYPoints[5] = y + height;
        break;
    }
    g.setStroke(myAnimationStroke);
    g.setColor(colorSet.getHighlightedFrames());
    g.drawPolyline(mXPoints, mYPoints, mXPoints.length);

    g.setStroke(myBasicStroke);
    g.setColor(colorSet.getAnchorDisconnectionCircle());

    int dx = 4 * Math.abs(Integer.signum(mXPoints[4] - mXPoints[1]));
    int dy = 4 * Math.abs(Integer.signum(mYPoints[4] - mYPoints[1]));
    int x = Math.min(mXPoints[1], mXPoints[4]) - 1 + dx;
    int y = Math.min(mYPoints[1], mYPoints[4]) - 1 + dy;
    int w = 4 + Math.abs(mXPoints[1] - mXPoints[4]) - dx;
    int h = 4 + Math.abs(mYPoints[1] - mYPoints[4]) - dy;
    g.fillRoundRect(x, y, w, h, 4, 4);

    g.setStroke(previousStroke);
    g.setColor(previousColor);
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + mDirection;
  }

  public static void add(DisplayList list, @AndroidDpCoordinate Rectangle rect, int direction
  ) {
    list.add(new DrawAnimatedFrame(rect.x, rect.y, rect.width, rect.height, direction));
  }
}
