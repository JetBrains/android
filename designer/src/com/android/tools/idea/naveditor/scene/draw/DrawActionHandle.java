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
package com.android.tools.idea.naveditor.scene.draw;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * {@linkplain DrawActionHandle} is responsible for rendering the action handle
 * on the right side of a nav screen in the nav editor. The handle appears as two
 * concentric circles. It supports a size changing animation defined by the initial
 * and final sizes and the duration of the size change.
 */
public class DrawActionHandle extends NavBaseDrawCommand {
  public static final int BACKGROUND_RADIUS = 6;
  public static final int SMALL_RADIUS = 8;
  public static final int LARGE_RADIUS = 12;
  public static final int BORDER_THICKNESS = 3;
  public static final int MAX_DURATION = 200;

  @SwingCoordinate private final int myX;
  @SwingCoordinate private final int myY;
  @SwingCoordinate private final int myInitialRadius;
  @SwingCoordinate private final int myFinalRadius;
  private final Color myBorderColor;
  private final Color myFillColor;

  private long myStartTime = -1;

  public DrawActionHandle(@SwingCoordinate int x,
                          @SwingCoordinate int y,
                          @SwingCoordinate int initialRadius,
                          @SwingCoordinate int finalRadius,
                          @NotNull Color borderColor,
                          @NotNull Color fillColor) {
    myX = x;
    myY = y;
    myInitialRadius = initialRadius;
    myFinalRadius = finalRadius;
    myBorderColor = borderColor;
    myFillColor = fillColor;
  }

  @Override
  public int getLevel() {
    return DRAW_ACTION_HANDLE;
  }

  @Override
  @NotNull
  protected Object[] getProperties() {
    return new Object[]{myX, myY, myInitialRadius, myFinalRadius, String.format("%x", myBorderColor.getRGB()),
      String.format("%x", myFillColor.getRGB())};
  }

  @Override
  public void paint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    long currentTime = sceneContext.getTime();

    if (myStartTime == -1) {
      myStartTime = currentTime;
    }

    int delta = myFinalRadius - myInitialRadius;
    int duration = Math.abs(delta) * MAX_DURATION / LARGE_RADIUS;
    int elapsed = (int)(currentTime - myStartTime);

    int r = myFinalRadius;

    if (elapsed < duration) {
      r = myInitialRadius + delta * elapsed / duration;
    }

    fillCircle(g, myX, myY, Math.max(r, BACKGROUND_RADIUS), myBorderColor);
    fillCircle(g, myX, myY, r - BORDER_THICKNESS, myFillColor);

    if (r != myFinalRadius) {
      sceneContext.repaint();
    }
  }

  private static void fillCircle(Graphics2D g, int x, int y, int r, Color color) {
    g.setColor(color);
    g.fillOval(x - r, y - r, 2 * r, 2 * r);
  }
}