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


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Crops blank pixels from the edges of the image and returns the cropped result. We
   * crop off pixels that are blank (meaning they have an alpha value = 0). Note that
   * this is not the same as pixels that aren't opaque (an alpha value other than 255).
   *
   * @param image       the image to be cropped
   * @param initialCrop If not null, specifies a rectangle which contains an initial
   *                    crop to continue. This can be used to crop an image where you already
   *                    know about margins in the image
   * @return a cropped version of the source image, or null if the whole image was blank
   *         and cropping completely removed everything
   */
  @Nullable
  public static BufferedImage cropBlank(@NotNull BufferedImage image, @Nullable Rectangle initialCrop) {
    return cropBlank(image, initialCrop, image.getType());
  }

  /**
   * Crops blank pixels from the edges of the image and returns the cropped result. We
   * crop off pixels that are blank (meaning they have an alpha value = 0). Note that
   * this is not the same as pixels that aren't opaque (an alpha value other than 255).
   *
   * @param image       the image to be cropped
   * @param initialCrop If not null, specifies a rectangle which contains an initial
   *                    crop to continue. This can be used to crop an image where you already
   *                    know about margins in the image
   * @param imageType   the type of {@link BufferedImage} to create
   * @return a cropped version of the source image, or null if the whole image was blank
   *         and cropping completely removed everything
   */
  public static BufferedImage cropBlank(BufferedImage image, Rectangle initialCrop, int imageType) {
    CropFilter filter = new CropFilter() {
      @Override
      public boolean crop(BufferedImage bufferedImage, int x, int y) {
        int rgb = bufferedImage.getRGB(x, y);
        return (rgb & 0xFF000000) == 0x00000000;
        // TODO: Do a threshold of 80 instead of just 0? Might give better
        // visual results -- e.g. check <= 0x80000000
      }
    };
    return crop(image, filter, initialCrop, imageType);
  }

  private static BufferedImage crop(BufferedImage image, CropFilter filter, Rectangle initialCrop, int imageType) {
    if (image == null) {
      return null;
    }

    // First, determine the dimensions of the real image within the image
    int x1, y1, x2, y2;
    if (initialCrop != null) {
      x1 = initialCrop.x;
      y1 = initialCrop.y;
      x2 = initialCrop.x + initialCrop.width;
      y2 = initialCrop.y + initialCrop.height;
    }
    else {
      x1 = 0;
      y1 = 0;
      x2 = image.getWidth();
      y2 = image.getHeight();
    }

    // Nothing left to crop
    if (x1 == x2 || y1 == y2) {
      return null;
    }

    // This algorithm is a bit dumb -- it just scans along the edges looking for
    // a pixel that shouldn't be cropped. I could maybe try to make it smarter by
    // for example doing a binary search to quickly eliminate large empty areas to
    // the right and bottom -- but this is slightly tricky with components like the
    // AnalogClock where I could accidentally end up finding a blank horizontal or
    // vertical line somewhere in the middle of the rendering of the clock, so for now
    // we do the dumb thing -- not a big deal since we tend to crop reasonably
    // small images.

    // First determine top edge
    topEdge:
    for (; y1 < y2; y1++) {
      for (int x = x1; x < x2; x++) {
        if (!filter.crop(image, x, y1)) {
          break topEdge;
        }
      }
    }

    if (y1 == image.getHeight()) {
      // The image is blank
      return null;
    }

    // Next determine left edge
    leftEdge:
    for (; x1 < x2; x1++) {
      for (int y = y1; y < y2; y++) {
        if (!filter.crop(image, x1, y)) {
          break leftEdge;
        }
      }
    }

    // Next determine right edge
    rightEdge:
    for (; x2 > x1; x2--) {
      for (int y = y1; y < y2; y++) {
        if (!filter.crop(image, x2 - 1, y)) {
          break rightEdge;
        }
      }
    }

    // Finally determine bottom edge
    bottomEdge:
    for (; y2 > y1; y2--) {
      for (int x = x1; x < x2; x++) {
        if (!filter.crop(image, x, y2 - 1)) {
          break bottomEdge;
        }
      }
    }

    // No need to crop?
    if (x1 == 0 && y1 == 0 && x2 == image.getWidth() && y2 == image.getHeight()) {
      return image;
    }

    if (x1 == x2 || y1 == y2) {
      // Nothing left after crop -- blank image
      return null;
    }

    int width = x2 - x1;
    int height = y2 - y1;

    // Now extract the sub-image
    if (imageType == -1) {
      imageType = image.getType();
    }
    if (imageType == BufferedImage.TYPE_CUSTOM) {
      imageType = BufferedImage.TYPE_INT_ARGB;
    }
    BufferedImage cropped = new BufferedImage(width, height, imageType);
    Graphics g = cropped.getGraphics();
    g.drawImage(image, 0, 0, width, height, x1, y1, x2, y2, null);

    g.dispose();

    return cropped;
  }

  /**
   * Interface implemented by cropping functions that determine whether
   * a pixel should be cropped or not.
   */
  private static interface CropFilter {
    /**
     * Returns true if the pixel is should be cropped.
     *
     * @param image the image containing the pixel in question
     * @param x     the x position of the pixel
     * @param y     the y position of the pixel
     * @return true if the pixel should be cropped (for example, is blank)
     */
    boolean crop(BufferedImage image, int x, int y);
  }
}
