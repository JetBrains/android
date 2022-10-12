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
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
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
import java.awt.Stroke;
import org.jetbrains.annotations.NotNull;

/**
 * Support Buttons
 */
public class ButtonDecorator extends SceneDecorator {
  public static class DrawButton extends DrawTextRegion {

    public static int androidToSwingFontSize(float fontSize) {
      return Math.round((fontSize * 2f + 4.5f) / 2.41f);
    }

    DrawButton(@SwingCoordinate int x,
               @SwingCoordinate int y,
               @SwingCoordinate int width,
               @SwingCoordinate int height,
               int mode,
               int baseLineOffset,
               float scale,
               int fontSize,
               String string) {
      super(x, y, width, height, mode, baseLineOffset, string, true, false,
            TEXT_ALIGNMENT_CENTER, TEXT_ALIGNMENT_VIEW_START, fontSize, scale);
      mHorizontalPadding = (int)(4 * scale);
      mVerticalPadding = (int)(8 * scale);
      mHorizontalMargin = (int)(8 * scale);
      mVerticalMargin = (int)(12 * scale);
    }

    @NotNull
    public static DrawButton createFromString(@NotNull String s) {
      String[] sp = s.split(",");
      int c = 0;
      int x = Integer.parseInt(sp[c++]);
      int y = Integer.parseInt(sp[c++]);
      int width = Integer.parseInt(sp[c++]);
      int height = Integer.parseInt(sp[c++]);
      int mode = Integer.parseInt(sp[c++]);
      int baseLineOffset = Integer.parseInt(sp[c++]);
      float scale = java.lang.Float.parseFloat(sp[c++]);
      int fontSize = java.lang.Integer.parseInt(sp[c++]);
      String text = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"'));

      return new DrawButton(x, y, width, height, mode, baseLineOffset, scale, fontSize, text);
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
             "," +
             mFontSize +
             ",\"" +
             mText +
             "\"";
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      ColorSet colorSet = sceneContext.getColorSet();
      if (colorSet.drawBackground()) {
        Stroke stroke = g.getStroke();
        int strokeWidth = sceneContext.getSwingDimensionDip(2);
        g.setStroke(new BasicStroke(strokeWidth));
        g.setColor(colorSet.getButtonBackground());
        g.fillRect(x + mHorizontalMargin, y + mVerticalMargin,
                   width - mHorizontalMargin * 2 + 1, height - mVerticalMargin * 2 + 1);
        g.setColor(colorSet.getFakeUI());
        g.drawRoundRect(x + mHorizontalMargin, y + mVerticalMargin,
                   width - mHorizontalMargin * 2, height - mVerticalMargin * 2,2,2);
        g.setStroke(stroke);
      }
      super.paint(g, sceneContext);
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
    String text = getContentText(component.getNlComponent());
    int fontSize = DrawTextRegion.getFont(component.getNlComponent(), "14sp");
    float scale = (float)sceneContext.getScale();
    int baseLineOffset = sceneContext.getSwingDimensionDip(component.getBaseline());
    int mode = component.isSelected() ? DecoratorUtilities.ViewStates.SELECTED_VALUE : DecoratorUtilities.ViewStates.NORMAL_VALUE;
    list.add(new DrawButton(l, t, w, h, mode, baseLineOffset, scale, fontSize, text));
  }

  @NotNull
  protected String getContentText(NlComponent nlComponent) {
    return nlComponent.getTagName();
  }
}
