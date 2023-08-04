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
import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawTextRegion;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import org.jetbrains.annotations.NotNull;

/**
 * Support Buttons
 */
public class RadioButtonDecorator extends SceneDecorator {
  public static class DrawRadioButton extends DrawTextRegion {
    int[] xp = new int[3];
    int[] yp = new int[3];

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
    }

    DrawRadioButton(@SwingCoordinate int x,
                    @SwingCoordinate int y,
                    @SwingCoordinate int width,
                    @SwingCoordinate int height,
                    int mode,
                    int baselineOffset,
                    float scale,
                    String text) {
      super(x, y, width, height, mode, baselineOffset, text, true, false, DrawTextRegion.TEXT_ALIGNMENT_VIEW_START,
            DrawTextRegion.TEXT_ALIGNMENT_CENTER, 32, scale);
    }

    @NotNull
    public static DrawRadioButton createFromString(@NotNull String s) {
      String[] sp = s.split(",");
      int c = 0;
      int x = Integer.parseInt(sp[c++]);
      int y = Integer.parseInt(sp[c++]);
      int width = Integer.parseInt(sp[c++]);
      int height = Integer.parseInt(sp[c++]);
      int mode = Integer.parseInt(sp[c++]);
      int baseLineOffset = Integer.parseInt(sp[c++]);
      float scale = java.lang.Float.parseFloat(sp[c++]);
      String text = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"'));

      return new DrawRadioButton(x, y, width, height, mode, baseLineOffset, scale, text);
    }

    @Override
    public String serialize() {
      return this.getClass().getSimpleName() +
             "," +
             x +
             "," +
             y +
             "," +
             width +
             "," +
             height +
             "," +
             mMode +
             "," +
             myBaseLineOffset +
             "," +
             mScale +
             ",\"" +
             mText +
             "\"";
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      int margin = height / 5;
      mHorizontalPadding = height;
      super.paint(g, sceneContext);
      ColorSet colorSet = sceneContext.getColorSet();
      if (colorSet.drawBackground()) {
        Shape original = g.getClip();
        g.clipRect(x, y, width, height);
        Stroke stroke = g.getStroke();
        g.setStroke(new BasicStroke(2));
        g.setColor(colorSet.getFakeUI());
        int side = height - margin * 2;
        g.drawRoundRect(x + margin, y + margin, side, side, side, side);
        g.setStroke(stroke);
        g.setClip(original);
      }
    }
  }

  @Override
  public void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    super.addContent(list, time, sceneContext, component);
    @AndroidDpCoordinate Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    @SwingCoordinate int l = sceneContext.getSwingXDip(rect.x);
    @SwingCoordinate int t = sceneContext.getSwingYDip(rect.y);
    @SwingCoordinate int w = sceneContext.getSwingDimensionDip(rect.width);
    @SwingCoordinate int h = sceneContext.getSwingDimensionDip(rect.height);
    String text = ConstraintUtilities.getResolvedText(component.getNlComponent());
    int baseLineOffset = sceneContext.getSwingDimensionDip(component.getBaseline());
    float scale = (float)sceneContext.getScale();
    int mode = component.isSelected() ? DecoratorUtilities.ViewStates.SELECTED_VALUE : DecoratorUtilities.ViewStates.NORMAL_VALUE;
    list.add(new DrawRadioButton(l, t, w, h, mode, baseLineOffset, scale, text));
  }
}
