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
  @SwingCoordinate public static final int INNER_CIRCLE_THICKNESS = 3;
  public static final float INNER_CIRCLE_FRACTION = 0.8f;

  @SwingCoordinate private final int myX;
  @SwingCoordinate private final int myY;
  @SwingCoordinate private final int myInitialRadius;
  @SwingCoordinate private final int myFinalRadius;
  private final Color myBorderColor;
  private final Color myFillColor;
  private final int myDuration;

  private long myStartTime = -1;

  public DrawActionHandle(@SwingCoordinate int x,
                          @SwingCoordinate int y,
                          @SwingCoordinate int initialRadius,
                          @SwingCoordinate int finalRadius,
                          @NotNull Color borderColor,
                          @NotNull Color fillColor,
                          int duration) {
    myX = x;
    myY = y;
    myInitialRadius = initialRadius;
    myFinalRadius = finalRadius;
    myBorderColor = borderColor;
    myFillColor = fillColor;
    myDuration = duration;
  }

  @Override
  public int getLevel() {
    return DRAW_ACTION_HANDLE;
  }

  @Override
  @NotNull
  protected Object[] getProperties() {
    return new Object[]{myX, myY, myInitialRadius, myFinalRadius, String.format("%x", myBorderColor.getRGB()),
      String.format("%x", myFillColor.getRGB()), myDuration};
  }

  @Override
  public void paint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    long currentTime = sceneContext.getTime();

    if (myStartTime == -1) {
      myStartTime = currentTime;
    }

    int delta = myFinalRadius - myInitialRadius;
    int elapsed = (int)(currentTime - myStartTime);

    Graphics2D g2 = (Graphics2D)g.create();

    @SwingCoordinate int r = (elapsed < myDuration)
                             ? myInitialRadius + delta * elapsed / myDuration
                             : myFinalRadius;
    fillCircle(g2, r, myFillColor);

    r *= INNER_CIRCLE_FRACTION;
    fillCircle(g2, r, myBorderColor);

    r -= INNER_CIRCLE_THICKNESS;
    fillCircle(g2, r, myFillColor);

    g2.dispose();

    if (elapsed < myDuration) {
      sceneContext.repaint();
    }
  }

  private void fillCircle(Graphics2D g, @SwingCoordinate int r, Color color) {
    g.setColor(color);
    g.fillOval(myX - r, myY - r, 2 * r, 2 * r);
  }
}