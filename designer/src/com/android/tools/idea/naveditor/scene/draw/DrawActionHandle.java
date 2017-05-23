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
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DrawCommand;

import java.awt.Color;
import java.awt.Graphics2D;

import org.jetbrains.annotations.NotNull;

/**
 * {@linkplain DrawActionHandle} is responsible for rendering the action handle
 * on the right side of a nav screen in the nav editor. The handle appears as two
 * concentric circles. It supports a size changing animation defined by the initial
 * and final sizes and the duration of the size change.
 */
public class DrawActionHandle implements DrawCommand {
  public static final int BORDER_THICKNESS = 2;

  @SwingCoordinate private final int myX;
  @SwingCoordinate private final int myY;
  @SwingCoordinate private final int myInitialRadius;
  @SwingCoordinate private final int myFinalRadius;
  private final Color myBackground;
  private final Color myCenter;
  private final int myDuration;

  private long myStartTime = 0;

  public DrawActionHandle(@SwingCoordinate int x,
                          @SwingCoordinate int y,
                          @SwingCoordinate int initialRadius,
                          @SwingCoordinate int finalRadius,
                          @NotNull Color background,
                          @NotNull Color center,
                          int duration) {
    myX = x;
    myY = y;
    myInitialRadius = initialRadius;
    myFinalRadius = finalRadius;
    myBackground = background;
    myCenter = center;
    myDuration = duration;
  }

  @Override
  public int getLevel() {
    return DrawCommand.TARGET_LEVEL;
  }

  @Override
  public
  @NotNull
  String serialize() {
    return this.getClass().getSimpleName() + "," + myX + "," + myY + "," + myInitialRadius + "," + myFinalRadius + "," + myDuration;
  }

  @Override
  public void paint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    long currentTime = sceneContext.getTime();

    if (myStartTime == 0) {
      myStartTime = currentTime;
    }

    @SwingCoordinate int r = myFinalRadius;

    if (currentTime < myStartTime + myDuration) {
      r = myInitialRadius + (int)(currentTime - myStartTime) * (myFinalRadius - myInitialRadius) / myDuration;
    }

    fillCircle(g, myX, myY, r, myBackground);
    fillCircle(g, myX, myY, r - BORDER_THICKNESS, myCenter);

    if (r != myFinalRadius) {
      sceneContext.repaint();
    }
  }

  private static void fillCircle(Graphics2D g, int x, int y, int r, Color color) {
    g.setColor(color);
    g.fillOval(x - r, y - r, 2 * r, 2 * r);
  }
}