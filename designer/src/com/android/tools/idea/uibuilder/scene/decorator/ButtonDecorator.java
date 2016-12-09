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

import com.android.SdkConstants;
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
 * Support Buttons
 */
public class ButtonDecorator extends SceneDecorator {
  public static class DrawButton extends DrawTextRegion {
    String myString;
    float mScale;

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
    }

    DrawButton(int x, int y, int width, int height, int baseLineOffset, float scale, String string) {
      super(x, y, width, height, baseLineOffset, string);
      mHorizontalPadding = (int)(4 * scale);
      mVerticalPadding = (int)(8 * scale);
      mHorizontalMargin = (int)(8 * scale);
      mVerticalMargin = (int)(12 * scale);
      mScale = scale;
      mFont = mFont.deriveFont(mFont.getSize() * mScale);
      mToUpperCase = true;
      mAlignmentX = TEXT_ALIGNMENT_CENTER;
      mAlignmentY = TEXT_ALIGNMENT_CENTER;
    }

    public DrawButton(String s) {
      String[] sp = s.split(",");
      int c = 0;
      x = Integer.parseInt(sp[c++]);
      y = Integer.parseInt(sp[c++]);
      width = Integer.parseInt(sp[c++]);
      height = Integer.parseInt(sp[c++]);
      myBaseLineOffset = Integer.parseInt(sp[c++]);
      mScale = java.lang.Float.parseFloat(sp[c++]);
      mFont = mFont.deriveFont(mFont.getSize() * mScale);
      mText = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"'));
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
             myBaseLineOffset +
             "," +
             mScale +
             ",\"" +
             mText +
             "\"";
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      super.paint(g, sceneContext);
      if (sceneContext.getColorSet().drawBackground()) {
        int round = sceneContext.getSwingDimension(5);
        Stroke stroke = g.getStroke();
        int strokeWidth = sceneContext.getSwingDimension(3);
        g.setStroke(new BasicStroke(strokeWidth));
        g.drawRoundRect(x + mHorizontalMargin, y + mVerticalMargin, width - mHorizontalMargin * 2, height - mVerticalMargin * 2, round,
                        round);
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
    String text = ConstraintUtilities.getResolvedText(component.getNlComponent());
    float scale = (float)sceneContext.getScale();
    int baseLineOffset = sceneContext.getSwingDimension(component.getBaseline());
    list.add(new DrawButton(l, t, w, h, baseLineOffset, scale, text));
  }
}
