/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;


import java.awt.*;
import java.awt.image.BufferedImage;

import static java.awt.RenderingHints.*;

/**
 * Utilities related to image processing.
 */
public class ImageUtils {
  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @return the scaled image
   */
  public static BufferedImage scale(BufferedImage source, double xScale, double yScale) {
    return scale(source, xScale, yScale, 0, 0);
  }

  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @param rightMargin extra margin to add on the right
   * @param bottomMargin extra margin to add on the bottom
   * @return the scaled image
   */
  public static BufferedImage scale(BufferedImage source, double xScale, double yScale,
                                    int rightMargin, int bottomMargin) {
    int sourceWidth = source.getWidth();
    int sourceHeight = source.getHeight();
    int destWidth = Math.max(1, (int) (xScale * sourceWidth));
    int destHeight = Math.max(1, (int) (yScale * sourceHeight));
    int imageType = source.getType();
    if (imageType == BufferedImage.TYPE_CUSTOM) {
      imageType = BufferedImage.TYPE_INT_ARGB;
    }
    if (xScale > 0.5 && yScale > 0.5) {
      BufferedImage scaled =
        new BufferedImage(destWidth + rightMargin, destHeight + bottomMargin, imageType);
      Graphics2D g2 = scaled.createGraphics();
      g2.setComposite(AlphaComposite.Src);
      g2.setColor(new Color(0, true));
      g2.fillRect(0, 0, destWidth + rightMargin, destHeight + bottomMargin);

      if (xScale == 1 && yScale == 1) {
        g2.drawImage(source, 0, 0, null);
      } else {
        g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
        g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0, destWidth, destHeight, 0, 0, sourceWidth, sourceHeight,
                     null);
      }
      g2.dispose();
      return scaled;
    } else {
      // When creating a thumbnail, using the above code doesn't work very well;
      // you get some visible artifacts, especially for text. Instead use the
      // technique of repeatedly scaling the image into half; this will cause
      // proper averaging of neighboring pixels, and will typically (for the kinds
      // of screen sizes used by this utility method in the layout editor) take
      // about 3-4 iterations to get the result since we are logarithmically reducing
      // the size. Besides, each successive pass in operating on much fewer pixels
      // (a reduction of 4 in each pass).
      //
      // However, we may not be resizing to a size that can be reached exactly by
      // successively diving in half. Therefore, once we're within a factor of 2 of
      // the final size, we can do a resize to the exact target size.
      // However, we can get even better results if we perform this final resize
      // up front. Let's say we're going from width 1000 to a destination width of 85.
      // The first approach would cause a resize from 1000 to 500 to 250 to 125, and
      // then a resize from 125 to 85. That last resize can distort/blur a lot.
      // Instead, we can start with the destination width, 85, and double it
      // successfully until we're close to the initial size: 85, then 170,
      // then 340, and finally 680. (The next one, 1360, is larger than 1000).
      // So, now we *start* the thumbnail operation by resizing from width 1000 to
      // width 680, which will preserve a lot of visual details such as text.
      // Then we can successively resize the image in half, 680 to 340 to 170 to 85.
      // We end up with the expected final size, but we've been doing an exact
      // divide-in-half resizing operation at the end so there is less distortion.


      int iterations = 0; // Number of halving operations to perform after the initial resize
      int nearestWidth = destWidth; // Width closest to source width that = 2^x, x is integer
      int nearestHeight = destHeight;
      while (nearestWidth < sourceWidth / 2) {
        nearestWidth *= 2;
        nearestHeight *= 2;
        iterations++;
      }

      // If we're supposed to add in margins, we need to do it in the initial resizing
      // operation if we don't have any subsequent resizing operations.
      if (iterations == 0) {
        nearestWidth += rightMargin;
        nearestHeight += bottomMargin;
      }

      BufferedImage scaled = new BufferedImage(nearestWidth, nearestHeight, imageType);
      Graphics2D g2 = scaled.createGraphics();
      g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
      g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
      g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
      g2.drawImage(source, 0, 0, nearestWidth, nearestHeight,
                   0, 0, sourceWidth, sourceHeight, null);
      g2.dispose();

      sourceWidth = nearestWidth;
      sourceHeight = nearestHeight;
      source = scaled;

      for (int iteration = iterations - 1; iteration >= 0; iteration--) {
        int halfWidth = sourceWidth / 2;
        int halfHeight = sourceHeight / 2;
        if (iteration == 0) { // Last iteration: Add margins in final image
          scaled = new BufferedImage(halfWidth + rightMargin, halfHeight + bottomMargin,
                                     imageType);
        } else {
          scaled = new BufferedImage(halfWidth, halfHeight, imageType);
        }
        g2 = scaled.createGraphics();
        g2.setRenderingHint(KEY_INTERPOLATION,VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
        g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0,
                     halfWidth, halfHeight, 0, 0,
                     sourceWidth, sourceHeight,
                     null);
        g2.dispose();

        sourceWidth = halfWidth;
        sourceHeight = halfHeight;
        source = scaled;
        iterations--;
      }
      return scaled;
    }
  }
}
