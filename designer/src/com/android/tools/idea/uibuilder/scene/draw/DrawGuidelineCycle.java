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
package com.android.tools.idea.uibuilder.scene.draw;

import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.sherpa.drawing.ColorSet;

import java.awt.*;

/**
 *  Drawing of the guideline cycle
 */
public class DrawGuidelineCycle extends DrawRegion {
  public static final int BEGIN = 0;
  public static final int END = 1;
  public static final int PERCENT = 16;

  int myMode;

  protected Font mFont = new Font("Helvetica", Font.PLAIN, 14);

  @Override
  public int getLevel() {
    return POST_CLIP_LEVEL;
  }

  public DrawGuidelineCycle(String s) {
    super(s);
    String[] sp = s.split(",");
    int c = 0;
    c = super.parse(sp, c);
    myMode = Integer.parseInt(sp[c++]);
  }

  public DrawGuidelineCycle(int x, int y, int width, int height, int mode) {
    super(x, y, width, height);
    myMode = mode;
  }

  @Override
  public String serialize() {
    return this.getClass().getSimpleName() + "," + x + "," + y + "," + width + "," + height + "," + myMode;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    ColorSet colorSet = sceneContext.getColorSet();
    Color background = colorSet.getFrames();
    g.setColor(background);
    g.fillOval(x, y, width, height);
    g.drawOval(x, y, width, height);
    if (myMode == PERCENT) {
      String text = "%";
      g.setColor(colorSet.getText());
      g.setFont(mFont);
      FontMetrics fontMetrics = g.getFontMetrics();
      int tx = x + (width - fontMetrics.stringWidth(text))/2;
      int ty = y + (height / 2) + fontMetrics.getDescent();
      g.drawString(text, tx + 1, ty + 1);
    }
  }

  public static void add(DisplayList list, SceneContext transform, float left, float top, float right, float bottom, int mode) {
    int l = transform.getSwingX(left);
    int t = transform.getSwingY(top);
    int w = transform.getSwingDimension(right - left);
    int h = transform.getSwingDimension(bottom - top);
    list.add(new DrawGuidelineCycle(l, t, w, h, mode));
  }
}
