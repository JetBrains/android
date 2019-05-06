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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Draw Action
 */
public class DrawAction extends DrawRegion {
  private final NlIcon myIcon;
  private boolean myIsOver;
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
                    @NotNull NlIcon icon,
                    boolean isOver) {
    super(x, y, width, height);
    myIcon = icon;
    myIsOver = isOver;
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    int r = (int)(width * 0.3);
    ColorSet colorSet = sceneContext.getColorSet();
    g.setColor(colorSet.getComponentObligatoryBackground());
    g.fillRoundRect(x - 1, y - 1, width + 2, height + 2, r, r);
    if (myIsOver) {
      g.setColor(colorSet.getWidgetActionSelectedBackground());
      g.fillRoundRect(x, y, width, height, r, r);
      g.setColor(colorSet.getWidgetActionSelectedBorder());
      g.drawRoundRect(x, y, width, height, r, r);
    }
    else {
      g.setColor(colorSet.getWidgetActionBackground());
      g.fillRoundRect(x, y, width, height, r, r);
    }
    Color color = colorSet.getText();
    g.setColor(color);
    Icon icon = myIcon.getSelectedIcon(sceneContext);
    g.setFont(mFont);
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

  public static void add(@NotNull DisplayList list,
                         @NotNull SceneContext transform,
                         @AndroidDpCoordinate float left,
                         @AndroidDpCoordinate float top,
                         @AndroidDpCoordinate float right,
                         @AndroidDpCoordinate float bottom,
                         @NotNull NlIcon icon,
                         boolean isOver) {
    int l = transform.getSwingXDip(left);
    int t = transform.getSwingYDip(top);
    int w = transform.getSwingDimensionDip(right - left);
    int h = transform.getSwingDimensionDip(bottom - top);
    list.add(new DrawAction(l, t, w, h, icon, isOver));
  }
}
