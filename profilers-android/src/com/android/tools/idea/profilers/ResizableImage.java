/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.stdui;

import com.intellij.ui.JBColor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * This is an image which can be resized but maintains its aspect ratio.
 */
public class ResizableImage extends JComponent {
  private static final int SQUARE_SIZE = 15;
  private static final int[] SHADOW_ALPHAS = {5, 15, 25, 35, 40};
  private static final JBColor CHECKERBOARD_COLOR_MAIN = new JBColor(new Color(201, 201, 201), new Color(51, 54, 55));
  private static final JBColor CHECKERBOARD_COLOR_ALT = new JBColor(new Color(236, 236, 236), new Color(60, 63, 65));

  @NotNull private BufferedImage myImage;

  public ResizableImage(@NotNull BufferedImage image) {
    super();
    myImage = image;
  }

  @Override
  public Dimension getPreferredSize() {
    Rectangle bounds = getBounds();
    if (bounds.width == 0 && bounds.height == 0) {
      // We return the maximum size (full size of image plus borders) if the bounds were not set, which most likely is the case when this
      // component just got created. If it happens that there is just no room left in the parent component, then it doesn't matter what we
      // return here anyway.
      return getMaximumSize();
    }
    Insets padding = getPadding();
    Dimension preferredImageSize =
      calculatedScaledImageSize(new Dimension(bounds.width - padding.left - padding.right, bounds.height - padding.top - padding.bottom));
    if (preferredImageSize.width == 0 && preferredImageSize.height == 0) {
      return new Dimension();
    }
    return new Dimension(preferredImageSize.width + padding.left + padding.right, preferredImageSize.height + padding.top + padding.bottom);
  }

  @Override
  public boolean isPreferredSizeSet() {
    return true;
  }

  @Override
  public Dimension getMaximumSize() {
    Insets padding = getPadding();
    return new Dimension(myImage.getWidth() + padding.left + padding.right, myImage.getHeight() + padding.top + padding.bottom);
  }

  @Override
  public boolean isMaximumSizeSet() {
    return true;
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
  }

  @NotNull
  private Insets getPadding() {
    Insets insets = getInsets();
    insets.left += SHADOW_ALPHAS.length;
    insets.top += SHADOW_ALPHAS.length;
    insets.right = SHADOW_ALPHAS.length;
    insets.bottom = SHADOW_ALPHAS.length;
    return insets;
  }

  @NotNull
  private Dimension calculatedScaledImageSize(@NotNull Dimension availableImageSpace) {
    if (availableImageSpace.width <= 0 || availableImageSpace.height <= 0) {
      return new Dimension();
    }

    int availableWidth = availableImageSpace.width;
    int availableHeight = availableImageSpace.height;

    int imageWidth = myImage.getWidth();
    int imageHeight = myImage.getHeight();

    float sourceRatio = (float)imageWidth / imageHeight;

    // Don't allow the final size to be larger than the original image, in order to prevent small
    // images from stretching into a blurry mess.
    float scaledWidth = imageWidth;
    float scaledHeight = imageHeight;

    if (availableWidth < scaledWidth) {
      scaledWidth = availableWidth;
      scaledHeight = scaledWidth / sourceRatio;
    }
    if (availableHeight < scaledHeight) {
      scaledHeight = availableHeight;
      scaledWidth = scaledHeight * sourceRatio;
    }

    return new Dimension((int)Math.ceil(scaledWidth), (int)Math.ceil(scaledHeight)); // Image is slightly not to ratio, but it's fine.
  }

  @Override
  protected void paintComponent(Graphics g) {
    Insets padding = getPadding();
    Dimension scaledSize =
      calculatedScaledImageSize(new Dimension(getWidth() - padding.left - padding.right, getHeight() - padding.top - padding.bottom));

    if (scaledSize.width > 0 && scaledSize.height > 0) {
      paintShadow(g, scaledSize);
      paintCheckerboard(g, padding, scaledSize);
      paintImage(g, padding, scaledSize);
    }

    super.paintComponent(g);
  }

  private void paintImage(@NotNull Graphics g, @NotNull Insets padding, @NotNull Dimension scaledSize) {
    Graphics2D g2 = (Graphics2D)g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    // -1 to offset slightly to the top and to the left, to make the shadows thicker on the bottom and right.
    g2.drawImage(myImage, padding.left, padding.top, scaledSize.width + padding.left, scaledSize.height + padding.top, 0, 0,
                 myImage.getWidth(), myImage.getHeight(), null);
  }

  private static void fillRect(@NotNull Graphics g, int x, int y, int w, int h, int i, int j) {
    g.fillRect(x + i * SQUARE_SIZE, y + j * SQUARE_SIZE, Math.min(SQUARE_SIZE, w - (i + 1) * SQUARE_SIZE),
               Math.min(SQUARE_SIZE, h - (j + 1) * SQUARE_SIZE));
  }

  private static void paintCheckerboard(@NotNull Graphics g, @NotNull Insets padding, @NotNull Dimension scaledSize) {
    int squareX = (int)Math.ceil((float)scaledSize.width / SQUARE_SIZE);
    int squareY = (int)Math.ceil((float)scaledSize.height / SQUARE_SIZE);
    for (int i = 0; i < squareX; i++) {
      g.setColor(CHECKERBOARD_COLOR_MAIN);
      for (int j = i % 2; j < squareY; j += 2) {
        fillRect(g, padding.left, padding.top, scaledSize.width, scaledSize.height, i, j);
      }
      g.setColor(CHECKERBOARD_COLOR_ALT);
      for (int j = 1 - i % 2; j < squareY; j += 2) {
        fillRect(g, padding.left, padding.top, scaledSize.width, scaledSize.height, i, j);
      }
    }
  }

  private void paintShadow(@NotNull Graphics g, @NotNull Dimension scaledSize) {
    Insets insets = getInsets();
    int width = scaledSize.width + SHADOW_ALPHAS.length * 2;
    int height = scaledSize.height + SHADOW_ALPHAS.length * 2;

    for (int i = 0; i < SHADOW_ALPHAS.length; i++) {
      g.setColor(new Color(0, 0, 0, SHADOW_ALPHAS[i]));
      g.drawRoundRect(insets.left + i, insets.top + i, width - i * 2, height - i * 2, i * 2, i * 2);
    }
  }
}
