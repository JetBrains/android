/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.scene.draw;

import com.android.tools.idea.uibuilder.graphics.NlIcon;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;

import javax.swing.*;
import java.awt.*;

/**
 * Draw Action
 */
public class DrawAction extends DrawRegion {
  private final NlIcon myIcon;
  private boolean myIsOver;
  @SwingCoordinate private int mySrcX;
  @SwingCoordinate private int mySrcY;
  @SwingCoordinate private int mySrcWidth;
  @SwingCoordinate private int mySrcHeight;
  int myMode;

  protected Font mFont = new Font("Helvetica", Font.PLAIN, 14);

  @Override
  public int getLevel() {
    return POST_CLIP_LEVEL;
  }

  public DrawAction(@SwingCoordinate int x,
                    @SwingCoordinate int y,
                    @SwingCoordinate int width,
                    @SwingCoordinate int height,
                    @SwingCoordinate int src_x,
                    @SwingCoordinate int src_y,
                    @SwingCoordinate int src_width,
                    @SwingCoordinate int src_height,
                    NlIcon icon,
                    boolean isOver) {
    super(x, y, width, height);
    myIcon = icon;
    myIsOver = isOver;
    mySrcX = src_x;
    mySrcY = src_y;
    mySrcWidth = src_width;
    mySrcHeight = src_height;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    int r = (int)(width * 0.3);
    float distance = distance(sceneContext.getMouseX(), sceneContext.getMouseY(), mySrcX, mySrcY, mySrcWidth, y + height - mySrcY);
    if (distance>20) {
      return;
    }
    ColorSet colorSet = sceneContext.getColorSet();
    g.setColor(colorSet.getComponentObligatoryBackground());
    g.fillRoundRect(x - 2, y - 2, width + 4, height + 4, r, r);
    if (myIsOver) {
      g.setColor(colorSet.getSelectedBackground());
      g.fillRoundRect(x, y, width, height, r, r);
      g.setColor(colorSet.getFrames());
      g.drawRoundRect(x, y, width, height, r, r);
    }
    else {
      g.setColor(colorSet.getFrames());
      g.fillRoundRect(x, y, width, height, r, r);
    }
    Color color = colorSet.getText();
    g.setColor(color);
    Icon icon = myIcon.getSelectedIcon(sceneContext);
    g.setFont(mFont);
    FontMetrics fontMetrics = g.getFontMetrics();
    int iw = icon.getIconWidth();
    int ih = icon.getIconHeight();
    if (iw > width || ih > height) {
      double scale = Math.min(width / (double)iw, height / (double)ih);
      Graphics2D g2 = (Graphics2D)g.create();

      double tx = x + (width - iw * scale) / 2;
      double ty = y + (height - ih * scale) / 2;
      g2.translate(tx, ty);
      g2.scale(scale, scale);
      icon.paintIcon(null, g2, 0, 0);
    }
    else {
      int tx = x + (width - iw) / 2;
      int ty = y + (height - ih) / 2;
      icon.paintIcon(null, g, tx, ty);
    }
  }

  @Override
  public String serialize() {
    return super.serialize() + "," + myMode;
  }

  public static void add(DisplayList list,
                         SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float bottom,
                         Rectangle src,
                         NlIcon icon,
                         boolean isOver) {
    int l = transform.getSwingXDip(left);
    int t = transform.getSwingYDip(top);
    int w = transform.getSwingDimensionDip(right - left);
    int h = transform.getSwingDimensionDip(bottom - top);
    src.x = transform.getSwingXDip(src.x);
    src.y = transform.getSwingYDip(src.y);
    src.width = transform.getSwingDimensionDip(src.width);
    src.height = transform.getSwingDimensionDip(src.height);
    list.add(new DrawAction(l, t, w, h, src.x, src.y, src.width, src.height, icon, isOver));
  }
}
