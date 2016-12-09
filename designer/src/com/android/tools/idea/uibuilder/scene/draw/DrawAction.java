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
 * Draw Action
 */
public class DrawAction extends DrawRegion {
  public static final int CLEAR = 0;
  public static final int BASELINE = 1;
  public static final int CHAIN = 2;
  private boolean myIsOver;

  int myMode;

  protected Font mFont = new Font("Helvetica", Font.PLAIN, 14);

  public DrawAction(String s) {
    String[] sp = s.split(",");
    int c = 0;
    c = super.parse(sp, c);
    myMode = Integer.parseInt(sp[c++]);
  }

  public DrawAction(int x, int y, int width, int height, int mode, boolean isOver) {
    super(x, y, width, height);
    myMode = mode;
    myIsOver = isOver;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    int r = (int)(width * 0.3);
    ColorSet colorSet = sceneContext.getColorSet();
    g.setColor(colorSet.getBackground());
    g.fillRoundRect(x - 2, y - 2, width + 4, height + 4, r, r);
    if (myIsOver) {
      g.setColor(colorSet.getSelectedBackground());
      g.fillRoundRect(x, y, width, height, r, r);
      g.setColor(colorSet.getFrames());
      g.drawRoundRect(x, y, width, height, r, r);
    } else {
      g.setColor(colorSet.getFrames());
      g.fillRoundRect(x, y, width, height, r, r);
    }
    Color color = colorSet.getText();
    g.setColor(color);
    String text = "X";
    if (myMode == 1) {
      text = "B";
    } else if (myMode == 2) {
      text = "C";
    }
    g.setFont(mFont);
    FontMetrics fontMetrics = g.getFontMetrics();
    int tx = x + (width - fontMetrics.stringWidth(text))/2;
    int ty = y + (height / 2) + fontMetrics.getDescent();
    g.drawString(text, tx, ty);
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + myMode;
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         float left,
                         float top,
                         float right,
                         float bottom,
                         int mode,
                         boolean isOver) {
    int l = transform.getSwingX(left);
    int t = transform.getSwingY(top);
    int w = transform.getSwingDimension(right - left);
    int h = transform.getSwingDimension(bottom - top);
    list.add(new DrawAction(l, t, w, h, mode, isOver));
  }
}
