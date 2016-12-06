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

import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawRegion;
import com.android.tools.sherpa.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Support Buttons
 */
public class CheckBoxDecorator extends SceneDecorator {
  public static class DrawCheckbox extends DrawRegion {
    int[] xp = new int[3];
    int[] yp = new int[3];

    DrawCheckbox(int x, int y, int width, int height) {
      super(x, y, width, height);
    }

    public DrawCheckbox(String s) {
      String[] sp = s.split(",");
      super.parse(sp, 0);
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      if (sceneContext.getColorSet().drawBackground()) {
        Stroke stroke = g.getStroke();
        g.setStroke(new BasicStroke(2));
        g.setColor(sceneContext.getColorSet().getFrames());
        int margin = height / 5;
        g.drawRoundRect(x + margin, y + margin, height - margin * 2, height - margin * 2, 4, 4);
        margin *= 2;
        int xv = x + margin;
        int yv = y + margin;
        int h = height - margin * 2;
        xp[0] = xv;
        xp[1] = xv + h / 3;
        xp[2] = xv + h;
        yp[0] = yv + h / 2;
        yp[1] = yv + h;
        yp[2] = yv;
        g.drawPolyline(xp, yp, 3);
        g.setStroke(stroke);
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
    list.add(new DrawCheckbox(l, t, w, h));
  }
}
