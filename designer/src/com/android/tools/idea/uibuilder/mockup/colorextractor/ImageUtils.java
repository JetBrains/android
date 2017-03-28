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
package com.android.tools.idea.uibuilder.mockup.colorextractor;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashMap;

/**
 * Utility function to process images
 */
@SuppressWarnings("UndesirableClassUsage")
public final class ImageUtils {
  /**
   * Create a posterized version of image using only color from the list of {@link ExtractedColor}.
   * If a pixel is not in {@link ExtractedColor#getNeighborColor()}, that means it
   * was considered as noise by the clustering algorithm, and i
   *
   * @param image
   * @param colorsCollection
   * @return
   */
  static BufferedImage posterize(BufferedImage image, Collection<ExtractedColor> colorsCollection) {
    HashMap<Integer, Integer> rgbToExtractedColor = new HashMap<>();
    BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    int width = copy.getWidth();
    int height = copy.getHeight();

    int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
    for (int i = 0; i < pixels.length; i++) {
      int rgb = pixels[i];
      if (!rgbToExtractedColor.containsKey(rgb)) {
        for (ExtractedColor extractedColor : colorsCollection) {
          if (extractedColor.getNeighborColor().contains(rgb)) {
            // Cache the color already found
            rgbToExtractedColor.put(rgb, extractedColor.getColor());
          }
        }
      }
      Integer replacementColor = rgbToExtractedColor.get(rgb);
      if (replacementColor != null) {
        pixels[i] = replacementColor;
      }
      else {
        pixels[i] = JBColor.CYAN.getRGB();
      }
    }
    copy.setRGB(0, 0, width, height, pixels, 0, width);
    return copy;
  }

  /**
   * Create a scaled version of image with the same aspect ratio and with
   * the size of the longest edge equal to maxImageSize
   *
   * @param image        The image to create the scaled version from
   * @param maxImageSize The size of the longest edge of the scaled image
   * @return A new BufferedImage.
   */
  @VisibleForTesting
  public static BufferedImage createScaledImage(BufferedImage image, int maxImageSize) {
    int longestEdge = Math.max(image.getHeight(), image.getWidth());
    if (longestEdge < maxImageSize) {
      return image;
    }

    double scale = maxImageSize / (double)longestEdge;
    AffineTransform transform = new AffineTransform();
    transform.scale(scale, scale);

    int newWidth = (int)Math.round(image.getWidth() * scale);
    int newHeight = (int)Math.round(image.getHeight() * scale);
    BufferedImage newImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = newImage.createGraphics();
    g.drawImage(image, transform, null);
    return newImage;
  }
}
