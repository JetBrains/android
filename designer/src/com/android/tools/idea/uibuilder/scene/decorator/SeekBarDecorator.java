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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.sherpa.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * SeekBar support
 */
public class SeekBarDecorator extends SceneDecorator {
  public static class DrawSeekBar extends DrawRegion {

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
    }

    DrawSeekBar(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width, @SwingCoordinate int height) {
      super(x, y, width, height);
    }

    public DrawSeekBar(String s) {
      String[] sp = s.split(",");
      super.parse(sp, 0);
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      // Draw background
      ColorSet colorSet = sceneContext.getColorSet();
      if (colorSet.drawBackground()) {
        g.setColor(colorSet.getComponentBackground());
        g.fillRect(x, y, width, height);
        Shape origClip = g.getClip();
        g.clipRect(x, y, width, height);
        g.setColor(colorSet.getFakeUI());
        g.fillRoundRect(x + 2, y + height / 2 - height / 8, width / 2, height / 4, height / 4, height / 4);
        g.drawRoundRect(x + 2, y + height / 2 - height / 8, width - 4, height / 4, height / 4, height / 4);
        g.fillArc(x + width / 2 - height / 3, y + height / 6, 2 * height / 3, 2 * height / 3, 0, 360);
        g.setClip(origClip);
      }
    }
  }

  @Override
  public void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    int l = sceneContext.getSwingX(rect.x);
    int t = sceneContext.getSwingY(rect.y);
    int w = sceneContext.getSwingDimension(rect.width);
    int h = sceneContext.getSwingDimension(rect.height);
    list.add(new DrawSeekBar(l, t, w, h));
  }
}