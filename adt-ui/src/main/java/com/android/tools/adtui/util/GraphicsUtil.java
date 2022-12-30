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
package com.android.tools.adtui.util;

import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;

public final class GraphicsUtil {

  private static final int BACKGROUND_CELL_SIZE = 4;

  /**
   * Paints a checkered board style background. Each grid square is {@code cellSize} pixels.
   */
  public static void paintCheckeredBackground(Graphics g, Color backgroundColor, Color checkeredColor, Shape clip, int cellSize) {
    final Shape savedClip = g.getClip();
    ((Graphics2D)g).clip(clip);

    final Rectangle rect = clip.getBounds();
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
   * Paints a checkered board style background.
   */
  public static void paintCheckeredBackground(Graphics g, Shape clip) {
    //noinspection UseJBColor
    paintCheckeredBackground(g, Color.LIGHT_GRAY, Color.GRAY, clip, BACKGROUND_CELL_SIZE);
  }

  /**
   * Draws a centered string in the passed rectangle.
   * @param g the {@link Graphics} instance to draw to
   * @param rect the {@link Rectangle} to use as bounding box
   * @param str the string to draw
   * @param horzCentered if true, the string will be centered horizontally
   * @param vertCentered if true, the string will be centered vertically
   */
  public static void drawCenteredString(Graphics2D g, Rectangle rect, String str, boolean horzCentered, boolean vertCentered) {
    UIUtil.drawCenteredString(g, rect, str, horzCentered, vertCentered);
  }

  /**
   * Draws a centered string in the passed rectangle.
   * @param g the {@link Graphics} instance to draw to
   * @param rect the {@link Rectangle} to use as bounding box
   * @param str the string to draw
   */
  public static void drawCenteredString(Graphics2D g, Rectangle rect, String str) {
    UIUtil.drawCenteredString(g, rect, str);
  }
}
