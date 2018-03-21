/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene.decorator;

import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawTextRegion;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Decorator for Switch widget
 */
public class SwitchDecorator extends SceneDecorator {
  public static class DrawSwitch extends DrawTextRegion {
    private static final int MARGIN = 4;

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
    }

    DrawSwitch(int x, int y, int width, int height, int mode, int baseLineOffset, String string) {
      super(x, y, width, height, mode, baseLineOffset, string);
    }

    DrawSwitch(int x,
               int y,
               int width,
               int height,
               int mode,
               int baseLineOffset,
               boolean singleLine,
               boolean toUpperCase,
               int alignmentX,
               int alignmentY,
               String string) {
      super(x, y, width, height, mode, baseLineOffset, string, singleLine, toUpperCase, alignmentX, alignmentY, DEFAULT_FONT_SIZE, DEFAULT_SCALE);
    }

    @NotNull
    public static DrawSwitch createFromString(@NotNull String s) {
      String[] sp = s.split(",");
      int c = 0;
      int x = Integer.parseInt(sp[c++]);
      int y = Integer.parseInt(sp[c++]);
      int width = Integer.parseInt(sp[c++]);
      int height = Integer.parseInt(sp[c++]);
      int mode = Integer.parseInt(sp[c++]);
      int baseLineOffset = Integer.parseInt(sp[c++]);
      boolean singleLine = Boolean.parseBoolean(sp[c++]);
      boolean toUpperCase = Boolean.parseBoolean(sp[c++]);
      int alignmentX = Integer.parseInt(sp[c++]);
      int alignmentY = Integer.parseInt(sp[c++]);
      String text = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"'));

      return new DrawSwitch(x, y, width, height, mode, baseLineOffset,  text);
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      super.paint(g, sceneContext);
      ColorSet colorSet = sceneContext.getColorSet();
      if (colorSet.drawBackground()) {
        Shape origClip = g.getClip();
        g.clipRect(x, y, width, height);
        g.setColor(colorSet.getFakeUI());
        int sHeight = mFont.getSize() / 2;
        int sWidth = sHeight * 4;
        int sx = x + width - sWidth - MARGIN;
        int sy = y + (height - sHeight) / 2;
        g.drawRoundRect(sx, sy, sWidth, sHeight, sHeight, sHeight);
        int bh = sHeight * 2;
        int bx = sx - bh / 2;
        int by = sy + sHeight / 2 - bh / 2;
        g.fillRoundRect(bx, by, bh, bh, bh, bh);

        g.setClip(origClip);
      }
    }
  }

  @Override
  public void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    super.addContent(list, time, sceneContext, component);
    Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    int l = sceneContext.getSwingXDip(rect.x);
    int t = sceneContext.getSwingYDip(rect.y);
    int w = sceneContext.getSwingDimensionDip(rect.width);
    int h = sceneContext.getSwingDimensionDip(rect.height);
    int baseLineOffset = sceneContext.getSwingDimensionDip(component.getBaseline());
    String text = ConstraintUtilities.getResolvedText(component.getNlComponent());
    if (text == null) {
      text = "";
    }
    int mode = component.isSelected() ? DecoratorUtilities.ViewStates.SELECTED_VALUE : DecoratorUtilities.ViewStates.NORMAL_VALUE;
    list.add(new DrawSwitch(l, t, w, h, mode, baseLineOffset, text));
  }
}