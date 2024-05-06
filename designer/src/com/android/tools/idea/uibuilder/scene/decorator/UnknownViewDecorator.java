/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.common.scene.draw.DrawTextRegion;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;

/**
 * Decorator to draw the contents of views not defined in {@link NlSceneDecoratorFactory}.
 * Draws a decorator with its ID or class name as content in blueprint mode.
 */
public class UnknownViewDecorator extends SceneDecorator {
  private static final String DEFAULT_DIM = "14sp";

  /**
   * Draws the centered text of an unknown decorator. Scales down to fit, and scales up to the default size.
   */
  public static class DrawUnknownDecorator extends DrawRegion {
    protected static final float SCALE_ADJUST = .88f; // a factor to scale fonts from android to Java2d
    private String mText;
    private Font mFont;
    private float mSceneScale;
    private int mFontSize;

    DrawUnknownDecorator(@SwingCoordinate int x,
                         @SwingCoordinate int y,
                         @SwingCoordinate int width,
                         @SwingCoordinate int height,
                         int fontSize,
                         float sceneScale,
                         String text) {
      super(x, y, width, height);
      mText = text;
      mSceneScale = sceneScale;
      mFontSize = fontSize;
      mFont = new Font("Helvetica", Font.PLAIN, fontSize)
        .deriveFont(AffineTransform.getScaleInstance(sceneScale * SCALE_ADJUST, sceneScale * SCALE_ADJUST));
    }

    @NotNull
    public static DrawUnknownDecorator createFromString(@NotNull String s) {
      String[] sp = s.split(",");
      int c = 0;
      int x = Integer.parseInt(sp[c++]);
      int y = Integer.parseInt(sp[c++]);
      int width = Integer.parseInt(sp[c++]);
      int height = Integer.parseInt(sp[c++]);
      int fontSize = java.lang.Integer.parseInt(sp[c++]);
      float sceneScale = java.lang.Float.parseFloat(sp[c++]);
      String text = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"'));

      return new DrawUnknownDecorator(x, y, width, height, fontSize, sceneScale, text);
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
             mFontSize +
             "," +
             mSceneScale +
             ",\"" +
             mText +
             "\"";
    }

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      ColorSet colorSet = sceneContext.getColorSet();
      if (!colorSet.drawBackground()) {
        return;
      }
      g.setColor(sceneContext.getColorSet().getFrames());
      int stringWidth = g.getFontMetrics(mFont).stringWidth(mText);
      float scaleToFit = width / ((stringWidth * 3f) / 2);
      scaleToFit = Math.min(scaleToFit, 1.0f);
      Font originalFont = g.getFont();
      g.setFont(mFont.deriveFont(mFont.getSize() * scaleToFit));
      FontMetrics fontMetrics = g.getFontMetrics();
      Rectangle2D textBounds = fontMetrics.getStringBounds(mText, g);
      g.drawString(mText, x + (int)((width - textBounds.getWidth()) / 2f), y + (int)(height - (height - textBounds.getHeight()) / 2f));
      g.setFont(originalFont);
    }
  }

  @Override
  public void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    if (component.getChildCount() > 0) {
      return;
    }
    super.addContent(list, time, sceneContext, component);
    @AndroidDpCoordinate Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    @SwingCoordinate int l = sceneContext.getSwingXDip(rect.x);
    @SwingCoordinate int t = sceneContext.getSwingYDip(rect.y);
    @SwingCoordinate int w = sceneContext.getSwingDimensionDip(rect.width);
    @SwingCoordinate int h = sceneContext.getSwingDimensionDip(rect.height);
    NlComponent nlComponent = component.getNlComponent();
    String text = nlComponent.getId();
    if (text == null) {
      text = nlComponent.getTagName();
      text = text.substring(text.lastIndexOf('.') + 1);
    }
    int size = DrawTextRegion.getFont(nlComponent, DEFAULT_DIM);
    list.add(new DrawUnknownDecorator(l, t, w, h, size, (float)sceneContext.getScale(), text));
  }
}
