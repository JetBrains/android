/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.swing.util;

import java.awt.*;

public class GraphicsUtil {
  /**
   * Paints a checkered board style background. Each grid square is {@code cellSize} pixels.
   */
  public static void paintCheckeredBackground(Graphics g, Color backgroundColor, Color checkeredColor, Shape clip, int cellSize) {
    final Rectangle rect = clip.getBounds();
    final Shape savedClip = g.getClip();
    g.setClip(clip);

    g.setColor(backgroundColor);
    g.fillRect(rect.x, rect.y, rect.width, rect.height);

    g.setColor(checkeredColor);
    for (int dy = 0; dy * cellSize < rect.height; dy++) {
      for (int dx = dy % 2; dx * cellSize < rect.width; dx += 2) {
        g.fillRect(rect.x + dx * cellSize, rect.y + dy * cellSize, cellSize, cellSize);
      }
    }
    g.setClip(savedClip);
  }

  /**
   * Paints a checkered board style background. Each grid square is {@code cellSize} pixels.
   */
  public static void paintCheckeredBackground(Graphics g, Shape clip, int cellSize) {
    paintCheckeredBackground(g, Color.LIGHT_GRAY, Color.GRAY, clip, cellSize);
  }

  /**
   * Draw a cross.
   *
   * @param g The {@link Graphics} instance
   * @param rect {@link Rectangle} to paint the cross into
   * @param alpha Alpha level to use painting the cross
   */
  public static void drawCross(Graphics g, Rectangle rect, float alpha) {
    Color color = g.getColor();
    //noinspection UseJBColor
    g.setColor(new Color(color.getRGBComponents(null)[0],
                         color.getRGBComponents(null)[1],
                         color.getRGBComponents(null)[2],
                         0.5f));

    g.drawLine(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height);
    g.drawLine(rect.x, rect.height, rect.x + rect.width, rect.y);
    g.setColor(color);
  }
}
