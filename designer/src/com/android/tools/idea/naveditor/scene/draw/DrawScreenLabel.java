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
 * {@linkplain DrawScreenLabel} draws the name of the screen above the frame.
 */
public class DrawScreenLabel extends NavBaseDrawCommand {
  @SwingCoordinate private final int myX;
  @SwingCoordinate private final int myY;
  private final Color myColor;
  private final Font myFont;
  private final String myText;

  public DrawScreenLabel(@SwingCoordinate int x,
                         @SwingCoordinate int y,
                         @NotNull Color color,
                         @NotNull Font font,
                         @NotNull String text) {
    myX = x;
    myY = y;
    myColor = color;
    myFont = font;
    myText = text;
  }

  @Override
  public int getLevel() {
    return DRAW_SCREEN_LABEL;
  }

  @Override
  @NotNull
  protected Object[] getProperties() {
    return new Object[]{myX, myY, String.format("%x", myColor.getRGB()), myFont, myText};
  }


  @Override
  public void paint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    Color previousColor = g.getColor();
    Font previousFont = g.getFont();

    g.setColor(myColor);
    g.setFont(myFont);
    g.drawString(myText, myX, myY);

    g.setColor(previousColor);
    g.setFont(previousFont);
  }
}
