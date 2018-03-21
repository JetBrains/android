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
package com.android.tools.adtui;

import com.android.tools.adtui.model.event.EventAction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Interface to define how events should be rendered in the event timeline.
 */
public interface SimpleEventRenderer<E> {

  /**
   * Primary draw function for events. This function will get called only when an event is supposed to have something drawn.
   *
   * @param parent    The parent element to draw to.
   * @param g2d       The graphics object used to draw elements.
   * @param transform The coordinates on the screen where the event starts
   * @param length    The length of the event if the event has a unit of time associated with its rendering.
   * @param data      The EventAction data used to trigger this draw event. This data can contain some addtional information
   *                  used by the renderers such as the string passed via keyboard event. If this argument is null the renderer
   *                  is expected to ignore the additional data or is not expected to use it.
   */
  void draw(Component parent, Graphics2D g2d, AffineTransform transform, double length, @Nullable EventAction<E> data);

  default void draw(Component parent, Graphics2D g2d, AffineTransform transform, double length) {
    draw(parent, g2d, transform, length, null);
  }


  /**
   * Return an ImageIcon with border. The border surrounds the original icon and has a constant thickness.
   *
   * @param icon         The original icon.
   * @param margin       Thickness of the icon's border. The returned ImageIcon is 2*margin larger than then original
   *                     icon in height and width to reserve space to draw the border.
   * @param borderColor  Color of the icon's border.
   */
  static ImageIcon createImageIconWithBackgroundBorder(Icon icon, int margin, Color borderColor) {
    BufferedImage originalImage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    // Border image has a bigger size to fit the extra border
    BufferedImage borderImage =
      new BufferedImage(icon.getIconWidth() + margin * 2, icon.getIconHeight() + margin * 2, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = originalImage.createGraphics();

    icon.paintIcon(null, g2d, 0, 0);
    for (int y = 0; y < originalImage.getHeight(); ++y) {
      for (int x = 0; x < originalImage.getWidth(); ++x) {
        Color color = new Color(originalImage.getRGB(x, y), true);
        if (color.getAlpha() > 0) {
          for (int ny = y - margin; ny <= y + margin; ++ny) {
            for (int nx = x - margin; nx <= x + margin; ++nx) {
              if ((x - nx) * (x - nx) + (y - ny) * (y - ny) <= margin * margin) {
                // Shift original image right and down to keep it centered in the border image
                borderImage.setRGB(nx + margin, ny + margin, borderColor.getRGB());
              }
            }
          }
        }
      }
    }
    g2d = borderImage.createGraphics();
    icon.paintIcon(null, g2d, margin, margin);
    return new ImageIcon(borderImage);
  }
}
