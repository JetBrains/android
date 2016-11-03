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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * A quick-and-dirty color picker that sacrifices some colors for the sake of compactness.
 * Not all colors will be "pickable" as a rectangle in HSL colorspace is shown keeping S fixed
 * at 1 (i.e. showing the surface of the HSL cylinder).
 *
 * Do not use this as a general purpose color picker, as some colors are not shown (e.g. grays).
 */
public class QuickColorPicker extends JComponent {
  private static final int SEXTANT = JBUI.scale(60);
  private static final int WIDTH = 6 * SEXTANT;

  private final BufferedImage image;

  public QuickColorPicker(int height, final Listener listener) {
    //noinspection UndesirableClassUsage
    image = generate(new BufferedImage(WIDTH, height, BufferedImage.TYPE_3BYTE_BGR));
    MouseAdapter mouseHandler = new MouseAdapter() {
      private int color = 0;

      @Override
      public void mousePressed(MouseEvent e) {
        update(e.getX(), e.getY());
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        update(e.getX(), e.getY());
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        update(e.getX(), e.getY());
      }

      private void update(int x, int y) {
        x = Math.max(0, Math.min(x, image.getWidth() - 1));
        y = Math.max(0, Math.min(y, image.getHeight() - 1));

        int newColor = image.getRGB(x, y);
        if (newColor != color) {
          color = newColor;
          listener.onColorChanged(newColor);
        }
      }
    };
    addMouseListener(mouseHandler);
    addMouseMotionListener(mouseHandler);
    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
  }

  @Override
  public boolean contains(int x, int y) {
    return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight();
  }

  private static BufferedImage generate(BufferedImage image) {
    // Iterate over H and L, keeping S fixed at 1.
    for (int h = 0; h < WIDTH; h++) {
      for (int y = 0; y < image.getHeight(); y++) {
        float l = 1 - (float)y / (image.getHeight() - 1); // Top of image, y = 0, L = 1.

        // Convert HSL to RGB (https://en.wikipedia.org/wiki/HSL_and_HSV). Simplified with S = 1.
        float c = (1 - Math.abs(2 * l - 1)); // chroma
        float x = c * (1 - Math.abs((((float)h / SEXTANT) % 2) - 1));
        float r, g, b;
        switch (h / SEXTANT) {
          case 0: r = c; g = x; b = 0; break;
          case 1: r = x; g = c; b = 0; break;
          case 2: r = 0; g = c; b = x; break;
          case 3: r = 0; g = x; b = c; break;
          case 4: r = x; g = 0; b = c; break;
          case 5: r = c; g = 0; b = x; break;
          default: continue; // This should not happen, if it does - really, it won't - simply ignore that column.
        }
        float m = l - c / 2; // lightness adjustment
        int rr = (int)((r + m) * 255), gg = (int)((g + m) * 255), bb = (int)((b + m) * 255);
        image.setRGB(h, y, (rr << 16) | (gg << 8) | bb);
      }
    }
    return image;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    g.drawImage(image, 0, 0, this);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(image.getWidth(), image.getHeight());
  }

  public interface Listener {
    void onColorChanged(int rgb);
  }
}
