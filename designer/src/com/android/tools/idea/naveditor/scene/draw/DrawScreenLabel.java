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
import com.android.tools.idea.common.scene.draw.FontCache;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * {@linkplain DrawScreenLabel} draws the name of the screen above the frame.
 */
public class DrawScreenLabel extends NavBaseDrawCommand {
  private static final String FONT_NAME = "Default";
  @SwingCoordinate private static final int FONT_SIZE = 12;
  @SwingCoordinate private final int myX;
  @SwingCoordinate private final int myY;
  private final String myText;

  public DrawScreenLabel(@SwingCoordinate int x,
                         @SwingCoordinate int y,
                         @NotNull String text) {
    myX = x;
    myY = y;
    myText = text;
  }

  public DrawScreenLabel(String s) {
    this(parse(s, 3));
  }

  private DrawScreenLabel(String[] sp) {
    this(Integer.parseInt(sp[0]), Integer.parseInt(sp[1]), sp[2]);
  }

  @Override
  public int getLevel() {
    return DRAW_SCREEN_LABEL_LEVEL;
  }

  @Override
  @NotNull
  protected Object[] getProperties() {
    return new Object[]{myX, myY, myText};
  }

  @Override
  protected void onPaint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    g.setColor(sceneContext.getColorSet().getSubduedText());

    Font font = FontCache.INSTANCE.getFont(FONT_SIZE, scalingFactor(sceneContext.getScale()), FONT_NAME);
    g.setFont(font);

    g.drawString(myText, myX, myY);
  }

  private static float scalingFactor(double scale) {
    return (float)(scale * (2.0 - scale)); // keep font size slightly larger at smaller scales
  }
}
