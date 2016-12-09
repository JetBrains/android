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

import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawRegion;
import com.android.tools.idea.uibuilder.scene.draw.DrawTextRegion;
import com.android.tools.sherpa.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Decorator for Switch widget
 */
public class SwitchDecorator extends SceneDecorator {
  public static class DrawSwitch extends DrawTextRegion {
    private static final int MARGIN = 4;

    DrawSwitch(int x, int y, int width, int height, int baseLineOffset, String string) {
      super(x, y, width, height, baseLineOffset, string);
    }

    public DrawSwitch(String string) {
      String[] sp = string.split(",");
      int c = super.parse(sp, 0);
      myBaseLineOffset = Integer.parseInt(sp[c++]);
      mSingleLine = Boolean.parseBoolean(sp[c++]);
      mToUpperCase = Boolean.parseBoolean(sp[c++]);
      mAlignmentX = Integer.parseInt(sp[c++]);
      mAlignmentY = Integer.parseInt(sp[c++]);
      mText = string.substring(string.indexOf('\"') + 1, string.lastIndexOf('\"'));
      super.parse(sp, 0);
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      super.paint(g, sceneContext);
      g.drawRect(x, y, width, height);
      ColorSet colorSet = sceneContext.getColorSet();
      if (colorSet.drawBackground()) {
        Shape origClip = g.getClip();
        g.clipRect(x, y, width, height);
        g.setColor(Color.WHITE);
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
  public void buildList(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    super.buildList(list, time, sceneContext, component);
    Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    int l = sceneContext.getSwingX(rect.x);
    int t = sceneContext.getSwingY(rect.y);
    int w = sceneContext.getSwingDimension(rect.width);
    int h = sceneContext.getSwingDimension(rect.height);
    int baseLineOffset = sceneContext.getSwingDimension(component.getBaseline());
    String text = ConstraintUtilities.getResolvedText(component.getNlComponent());
    if (text == null) {
      text = "";
    }
    list.add(new DrawSwitch(l, t, w, h, baseLineOffset, text));
  }
}