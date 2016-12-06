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
 * Decorator for Switch widget
 */
public class SwitchDecorator extends SceneDecorator {
  public static class DrawSwitch extends DrawRegion {
    DrawSwitch(int x, int y, int width, int height) {
      super(x, y, width, height);
    }

    public DrawSwitch(String s) {
      String[] sp = s.split(",");
      super.parse(sp, 0);
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      g.drawRect(x, y, width, height);
      ColorSet colorSet = sceneContext.getColorSet();
      if (colorSet.drawBackground()) {
        Shape origClip = g.getClip();
        g.clipRect(x, y, width, height);
        g.setColor(Color.WHITE);
        g.fillRoundRect(x + 2, y + height / 2 - height / 8, width / 2, height / 4, height / 4, height / 4);
        g.drawRoundRect(x + 2, y + height / 2 - height / 8, width - 4, height / 4, height / 4, height / 4);
        g.fillArc(x + width / 2 - height / 3, y + height / 6, 2 * height / 3, 2 * height / 3, 0, 360);
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
    list.add(new DrawSwitch(l, t, w, h));
  }
}