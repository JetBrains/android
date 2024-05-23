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

import java.awt.*;

public class GraphicsUtil {

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
}
