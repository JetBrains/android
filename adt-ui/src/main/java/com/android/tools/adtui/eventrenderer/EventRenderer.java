/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.eventrenderer;

import com.android.tools.adtui.model.event.EventAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Interface to define how events should be rendered in the event timeline.
 */
public interface EventRenderer<E> {

  /**
   * Primary draw function for events. This function will get called only when an event is supposed to have something drawn.
   *
   * @param parent      The parent element to draw to.
   * @param g2d         The graphics object used to draw elements.
   * @param transform   The coordinates on the screen where the event starts
   * @param length      The length of the event if the event has a unit of time associated with its rendering.
   * @param isMouseOver True if the mouse is over the data element passed in, otherwise false.
   * @param data        The EventAction data used to trigger this draw event. This data can contain some addtional information
   *                    used by the renderers such as the string passed via keyboard event. If this argument is null the renderer
   *                    is expected to ignore the additional data or is not expected to use it.
   */
  void draw(@NotNull Component parent,
            @NotNull Graphics2D g2d,
            @NotNull AffineTransform transform,
            double length,
            boolean isMouseOver,
            @Nullable EventAction<E> data);

  default void draw(@NotNull Component parent,
                    @NotNull Graphics2D g2d,
                    @NotNull AffineTransform transform,
                    double length,
                    boolean isMouseOver) {
    draw(parent, g2d, transform, length, isMouseOver, null);
  }


  /**
   * Return an ImageIcon with border. The border surrounds the original icon and has a constant thickness.
   *
   * @param icon        The original icon.
   * @param margin      Thickness of the icon's border. The returned ImageIcon is 2*margin larger than then original
   *                    icon in height and width to reserve space to draw the border.
   * @param borderColor Color of the icon's border.
   * @param g2d Graphics object to be used for painting the icon, used to get the appropriate scaling.
   */
  static ImageIcon createImageIconWithBackgroundBorder(Icon icon, int margin, Color borderColor, Graphics2D g2d) {
    BufferedImage originalImage = ImageUtil.toBufferedImage(IconLoader.toImage(icon, ScaleContext.create(g2d)));
    // Border image has a bigger size to fit the extra border
    BufferedImage borderImage =
      ImageUtil.createImage(g2d, icon.getIconWidth() + margin * 2, icon.getIconHeight() + margin * 2, BufferedImage.TYPE_INT_ARGB);

    int scaledMargin = (borderImage.getHeight() - originalImage.getHeight()) / 2;
    for (int y = 0; y < originalImage.getHeight(); ++y) {
      for (int x = 0; x < originalImage.getWidth(); ++x) {
        Color color = new Color(originalImage.getRGB(x, y), true);
        if (color.getAlpha() > 0) {
          for (int ny = y - scaledMargin; ny <= y + scaledMargin; ++ny) {
            for (int nx = x - scaledMargin; nx <= x + scaledMargin; ++nx) {
              if ((x - nx) * (x - nx) + (y - ny) * (y - ny) <= scaledMargin * scaledMargin) {
                // Shift original image right and down to keep it centered in the border image
                borderImage.setRGB(nx + scaledMargin, ny + scaledMargin, borderColor.getRGB());
              }
            }
          }
        }
      }
    }
    Graphics2D borderGraphics = borderImage.createGraphics();
    icon.paintIcon(null, borderGraphics, margin, margin);
    return new JBImageIcon(borderImage);
  }
}
