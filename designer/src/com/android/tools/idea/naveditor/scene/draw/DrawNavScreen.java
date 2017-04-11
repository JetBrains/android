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

import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DrawCommand;
import com.google.common.base.Joiner;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * {@link DrawCommand} that draws a screen in the navigation editor.
 *
 * TODO: actually implement it
 */
public class DrawNavScreen implements DrawCommand {
  @SwingCoordinate private int myX;
  @SwingCoordinate private int myY;
  @SwingCoordinate private int myWidth;
  @SwingCoordinate private int myHeight;
  @NotNull private String myText;
  boolean mySelected;

  public DrawNavScreen(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width, @SwingCoordinate int height,
                       @NotNull String text, boolean selected) {
    myX = x;
    myY = y;
    myWidth = width;
    myHeight = height;
    mySelected = selected;
    myText = text;
  }

  @Override
  public int getLevel() {
    return 30;
  }

  @Override
  public void paint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    Color previousColor = g.getColor();
    Shape previousClip = g.getClip();
    g.setClip(myX, myY, myWidth, myHeight);
    g.setColor(mySelected ? sceneContext.getColorSet().getSelectedText() : sceneContext.getColorSet().getText());
    g.drawString(myText, myX, myY + 24);
    g.setColor(previousColor);
    g.setClip(previousClip);
  }

  @NotNull
  @Override
  public String serialize() {
    return Joiner.on(',').join(
      this.getClass().getSimpleName(),
      myX,
      myY,
      myWidth,
      myHeight,
      myText);
  }
}
