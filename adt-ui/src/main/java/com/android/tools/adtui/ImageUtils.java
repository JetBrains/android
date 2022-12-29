/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.intellij.util.ui.ImageUtil.applyQualityRenderingHints;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.KEY_RENDERING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_OFF;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
import static java.awt.RenderingHints.VALUE_RENDER_QUALITY;
import static java.awt.RenderingHints.VALUE_RENDER_SPEED;
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.android.annotations.concurrency.Slow;
import com.intellij.util.ui.ImageUtil;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities related to image processing.
 */
@SuppressWarnings("UndesirableClassUsage") // BufferedImage is ok, deliberately not creating Retina images in some cases
public class ImageUtils {
  public static final double EPSILON = 1e-5;

  /**
   * Filter that checks pixels for being completely transparent.
   */
  public static final CropFilter TRANSPARENCY_FILTER = (bufferedImage, x, y) -> {
    int rgb = bufferedImage.getRGB(x, y);
    return (rgb & 0xFF000000) == 0;
  };

  /**
   * Rotates the given image by the given number of quadrants.
   *
   * @param source the source image
   * @param numQuadrants the number of quadrants to rotate by counterclockwise
   * @return the rotated image
   */
  public static @NotNull BufferedImage rotateByQuadrants(@NotNull BufferedImage source, int numQuadrants) {
    numQuadrants = numQuadrants & 0x3;
    if (numQuadrants == 0) {
      return source;
    }

    int w = source.getWidth();
    int h = source.getHeight();

    int rotatedW;
    int rotatedH;
    int shiftX;
    int shiftY;
    switch (numQuadrants) {
      default:
        rotatedW = w;
        rotatedH = h;
        shiftX = 0;
        shiftY = 0;
        break;

      case 1:
        rotatedW = h;
        rotatedH = w;
        shiftX = 0;
        shiftY = w;
        break;

      case 2:
        rotatedW = w;
        rotatedH = h;
        shiftX = w;
        shiftY = h;
        break;

      case 3:
        rotatedW = h;
        rotatedH = w;
        shiftX = h;
        shiftY = 0;
        break;
    }

    BufferedImage result = new BufferedImage(rotatedW, rotatedH, source.getType());
    Graphics2D graphics = result.createGraphics();
    graphics.setRenderingHint(KEY_RENDERING, VALUE_RENDER_SPEED);
    graphics.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    AffineTransform transform = new AffineTransform();
    // Please notice that the transformations are applied in the reverse order, starting from rotation.
    transform.translate(shiftX, shiftY);
    transform.quadrantRotate(-numQuadrants);
    graphics.drawRenderedImage(source, transform);
    graphics.dispose();
    return result;
  }

  /**
   * Rotates the given image by the given number of quadrants and scales it to the given dimensions.
   *
   * @param source the source image
   * @param numQuadrants the number of quadrants to rotate by counterclockwise
   * @param destinationWidth the width of the resulting image
   * @param destinationHeight the height of the resulting image
   * @return the rotated and scaled image
   */
  public static @NotNull BufferedImage rotateByQuadrantsAndScale(
      @NotNull BufferedImage source, int numQuadrants, int destinationWidth, int destinationHeight) {
    numQuadrants = numQuadrants & 0x3;
    if (numQuadrants == 0 && destinationWidth == source.getWidth() && destinationHeight == source.getHeight()) {
      return source;
    }

    int w = source.getWidth();
    int h = source.getHeight();

    double rotatedW;
    double rotatedH;
    int shiftX;
    int shiftY;
    switch (numQuadrants) {
      case 0:
      default:
        rotatedW = w;
        rotatedH = h;
        shiftX = 0;
        shiftY = 0;
        break;

      case 1:
        rotatedW = h;
        rotatedH = w;
        shiftX = 0;
        shiftY = destinationHeight;
        break;

      case 2:
        rotatedW = w;
        rotatedH = h;
        shiftX = destinationWidth;
        shiftY = destinationHeight;
        break;

      case 3:
        rotatedW = h;
        rotatedH = w;
        shiftX = destinationWidth;
        shiftY = 0;
        break;
    }

    BufferedImage result = new BufferedImage(destinationWidth, destinationHeight, source.getType());
    AffineTransform transform = new AffineTransform();
    // Please notice that the transformations are applied in the reverse order, starting from rotation.
    transform.translate(shiftX, shiftY);
    transform.scale(destinationWidth / rotatedW, destinationHeight / rotatedH);
    transform.quadrantRotate(-numQuadrants);
    AffineTransformOp transformOp = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
    return transformOp.filter(source, result);
  }

  public static BufferedImage createDipImage(int width, int height, int type) {
    return ImageUtil.createImage(width, height, type);
  }

  /**
   * Returns a new image that is the source image surrounded by a transparent margin of given size.
   */
  public static BufferedImage addMargin(BufferedImage source, int marginSize) {
    int destWidth = source.getWidth() + 2 * marginSize;
    int destHeight = source.getHeight() + 2 * marginSize;

    // since we are adding a transparent margin, make sure destination image has an alpha channel
    int type = source.getColorModel().hasAlpha() ? source.getType() : BufferedImage.TYPE_INT_ARGB;

    BufferedImage expanded = new BufferedImage(destWidth, destHeight, type);
    Graphics2D g2 = expanded.createGraphics();
    //noinspection UseJBColor
    g2.setColor(new Color(0, true));
    g2.fillRect(0, 0, destWidth, destHeight);
    g2.drawImage(source, marginSize, marginSize, null);
    g2.dispose();

    return expanded;
  }

  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param amount to scale the image in both directions
   * @return the scaled image
   */
  @NotNull
  public static BufferedImage scale(BufferedImage source, double amount) {
    return scale(source, amount, amount, 0, 0);
  }

  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @return the scaled image
   */
  @NotNull
  public static BufferedImage scale(BufferedImage source, double xScale, double yScale) {
    return scale(source, xScale, yScale, 0, 0);
  }

  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @param clip   an optional clip rectangle to use
   * @return the scaled image
   */
  @NotNull
  public static BufferedImage scale(BufferedImage source, double xScale, double yScale, @Nullable Shape clip) {
    return scale(source, xScale, yScale, 0, 0, clip);
  }

  /**
   * Resize the given image
   *
   * @param source       the image to be scaled
   * @param xScale       x scale
   * @param yScale       y scale
   * @param rightMargin  extra margin to add on the right
   * @param bottomMargin extra margin to add on the bottom
   * @return the scaled image
   */
  @NotNull
  public static BufferedImage scale(BufferedImage source, double xScale, double yScale,
                                    int rightMargin, int bottomMargin) {
    return scale(source, xScale, yScale, rightMargin, bottomMargin, null);
  }

  /**
   * Resize the given image
   *
   * @param source       the image to be scaled
   * @param xScale       x scale
   * @param yScale       y scale
   * @param rightMargin  extra margin to add on the right
   * @param bottomMargin extra margin to add on the bottom
   * @param clip         an optional clip rectangle to use
   * @return the scaled image
   */
  @NotNull
  public static BufferedImage scale(BufferedImage source, double xScale, double yScale,
                                    int rightMargin, int bottomMargin, @Nullable Shape clip) {
    int sourceWidth = source.getWidth();
    int sourceHeight = source.getHeight();
    int destWidth = max(1, (int)(xScale * sourceWidth));
    int destHeight = max(1, (int)(yScale * sourceHeight));
    int imageType = source.getType();
    if (imageType == BufferedImage.TYPE_CUSTOM
        || imageType == BufferedImage.TYPE_BYTE_INDEXED
        || imageType == BufferedImage.TYPE_BYTE_BINARY) {
      imageType = BufferedImage.TYPE_INT_ARGB;
    }
    if (xScale > 0.5 && yScale > 0.5) {
      BufferedImage scaled =
        new BufferedImage(destWidth + rightMargin, destHeight + bottomMargin, imageType);
      Graphics2D g2 = scaled.createGraphics();
      g2.setComposite(AlphaComposite.Src);
      //noinspection UseJBColor
      g2.setColor(new Color(0, true));
      g2.fillRect(0, 0, destWidth + rightMargin, destHeight + bottomMargin);
      if (clip != null) {
        g2.setClip(clip);
      }
      if (xScale == 1 && yScale == 1) {
        g2.drawImage(source, 0, 0, null);
      }
      else {
        g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
        g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0, destWidth, destHeight, 0, 0, sourceWidth, sourceHeight,
                     null);
      }
      g2.dispose();
      return scaled;
    }
    else {
      // When creating a thumbnail, using the above code doesn't work very well;
      // you get some visible artifacts, especially for text. Instead, use the
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

      @SuppressWarnings("UndesirableClassUsage")
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
          g2 = scaled.createGraphics();
          if (clip != null) {
            g2.setClip(clip);
          }
        }
        else {
          scaled = new BufferedImage(halfWidth, halfHeight, imageType);
          g2 = scaled.createGraphics();
        }
        g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
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
   * Do a fast, low-quality, scaling of the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @return the scaled image
   */
  @NotNull
  public static BufferedImage lowQualityFastScale(@NotNull BufferedImage source, double xScale, double yScale) {
    return lowQualityFastScale(source, xScale, yScale, 0, 0, null);
  }

  /**
   * Does a fast, low-quality, scaling of the given image
   *
   * @param source       the image to be scaled
   * @param xScale       x scale
   * @param yScale       y scale
   * @param rightMargin  extra margin to add on the right
   * @param bottomMargin extra margin to add on the bottom
   * @param clip         an optional clip rectangle to use
   * @return the scaled image
   */
  @NotNull
  public static BufferedImage lowQualityFastScale(@NotNull BufferedImage source, double xScale, double yScale,
                                                  int rightMargin, int bottomMargin, @Nullable Shape clip) {
    int sourceWidth = source.getWidth();
    int sourceHeight = source.getHeight();
    int destWidth = max(1, (int)(xScale * sourceWidth));
    int destHeight = max(1, (int)(yScale * sourceHeight));
    int imageType = source.getType();
    if (imageType == BufferedImage.TYPE_CUSTOM) {
      imageType = BufferedImage.TYPE_INT_ARGB;
    }
    BufferedImage scaled = new BufferedImage(destWidth + rightMargin, destHeight + bottomMargin, imageType);
    Graphics2D g2 = scaled.createGraphics();
    g2.setComposite(AlphaComposite.Src);
    //noinspection UseJBColor
    g2.setColor(new Color(0, true));
    g2.fillRect(0, 0, destWidth + rightMargin, destHeight + bottomMargin);
    if (clip != null) {
      g2.setClip(clip);
    }
    if (xScale == 1 && yScale == 1) {
      g2.drawImage(source, 0, 0, null);
    }
    else {
      g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_SPEED);
      g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_OFF);
      g2.drawImage(source, 0, 0, destWidth, destHeight, 0, 0, sourceWidth, sourceHeight, null);
    }
    g2.dispose();
    return scaled;
  }

  /**
   * Creates a {@link BufferedImage} from the provided inputStream and scale it to fit
   * into the provided dimension while keeping the original aspect ratio.
   * <p>
   * The particularity of this method is that if the original image is more than twice
   * the size of the target dimensions, it doesn't load the full image in memory
   * but only reads enough pixels to have quality good enough for the target size.
   * <p>
   * For example, if an image measures 100x100 pixels, and the target dimension
   * is 10x10 (10 times smaller), only the pixels at x and y coordinates
   * 0, 9, 19,..., 99 will be read.
   * <p>
   * See {@link ImageReadParam#setSourceSubsampling(int, int, int, int)} for more details.
   *
   * @param dimension   The dimension in which the image will be rendered.
   *                    The image will keep its original aspect ratio and will
   *                    be fitted inside these dimension.
   * @param inputStream the input
   * @return the image file as a {@link BufferedImage}
   * @see ImageReadParam#setSourceSubsampling(int, int, int, int)
   */
  @Slow
  public static @Nullable BufferedImage readImageAtScale(@NotNull InputStream inputStream, @NotNull Dimension dimension)
      throws IOException {
    ImageInputStream imageStream = ImageIO.createImageInputStream(inputStream);

    // Find all image readers that recognize the image format
    Iterator<ImageReader> readerIterator = ImageIO.getImageReaders(imageStream);
    if (!readerIterator.hasNext()) {
      return null;
    }

    ImageReader reader = readerIterator.next();
    reader.setInput(imageStream);
    ImageReadParam readParams = reader.getDefaultReadParam();

    double srcW = reader.getWidth(0);
    double srcH = reader.getHeight(0);
    double scale = srcW > srcH ? dimension.width / srcW : dimension.height / srcH;

    // If the target size is at least twice as small as the origin, we do the subsampling.
    // Otherwise, we just scale.
    if (scale < 0.5) {
      // Because subsampling actually skip pixels, the end result quality is lower
      // than a downscaling (which average neighbouring pixels). To minimize the loss
      // of quality, we double the initial scale value so the subsampling is reduced and
      // replaced by a downscaling step.
      scale *= 2;
      double xStep = Math.floor(1 / scale);
      double yStep = Math.floor(1 / scale);

      readParams.setSourceSubsampling((int)xStep, (int)yStep, 0, 0);
    }

    // Read the image, with the optional downsampling.
    BufferedImage intermediateImage = reader.read(0, readParams);

    imageStream.close();
    inputStream.close();

    // Do a final scale to be sure that the image fits in the provided dimension
    scale = srcW > srcH ? dimension.width / (double)intermediateImage.getWidth() : dimension.height / (double)intermediateImage.getHeight();
    return scale(intermediateImage, scale, scale);
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
   * and cropping completely removed everything
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
   * and cropping completely removed everything
   */
  @Nullable
  public static BufferedImage cropBlank(@Nullable BufferedImage image, @Nullable Rectangle initialCrop, int imageType) {
    return crop(image, TRANSPARENCY_FILTER, initialCrop, imageType);
  }

  /**
   * Determines the crop bounds for the given image.
   *
   * @param image       the image to be cropped
   * @param filter      the filter determining whether a pixel is blank or not
   * @param initialCrop If not null, specifies a rectangle which contains an initial
   *                    crop to continue. This can be used to crop an image where you already
   *                    know about margins in the image
   * @return the bounds of the crop in the given image, or null if the whole image was blank
   * and cropping completely removed everything
   */
  @Nullable
  public static Rectangle getCropBounds(@Nullable BufferedImage image, @NotNull CropFilter filter, @Nullable Rectangle initialCrop) {
    if (image == null) {
      return null;
    }

    // First, determine the dimensions of the real image within the image.
    int x1, y1, x2, y2;
    if (initialCrop != null) {
      x1 = max(initialCrop.x, 0);
      y1 = max(initialCrop.y, 0);
      x2 = min(initialCrop.x + initialCrop.width, image.getWidth());
      y2 = min(initialCrop.y + initialCrop.height, image.getHeight());
    }
    else {
      x1 = 0;
      y1 = 0;
      x2 = image.getWidth();
      y2 = image.getHeight();
    }

    // Nothing left to crop.
    if (x1 == x2 || y1 == y2) {
      return null;
    }

    // This algorithm is linear with respect to the number of pixels in the cropped
    // area of the image. A sublinear algorithm is not possible since each cropped
    // pixel has to be examined at least once because the non-blank part of the image
    // may be disjoint.

    // First determine top edge.
    topEdge:
    for (; y1 < y2; y1++) {
      for (int x = x1; x < x2; x++) {
        if (!filter.crop(image, x, y1)) {
          break topEdge;
        }
      }
    }

    if (y1 == y2) {
      // The image is blank.
      return null;
    }

    // Next determine left edge.
    leftEdge:
    for (; x1 < x2; x1++) {
      for (int y = y1; y < y2; y++) {
        if (!filter.crop(image, x1, y)) {
          break leftEdge;
        }
      }
    }

    // Next determine right edge.
    rightEdge:
    while (--x2 >= x1) {
      for (int y = y1; y < y2; y++) {
        if (!filter.crop(image, x2, y)) {
          break rightEdge;
        }
      }
    }
    ++x2;

    // Finally determine bottom edge.
    bottomEdge:
    while (--y2 >= y1) {
      for (int x = x1; x < x2; x++) {
        if (!filter.crop(image, x, y2)) {
          break bottomEdge;
        }
      }
    }
    ++y2;

    if (x1 == x2 || y1 == y2) {
      // Nothing left after crop -- blank image
      return null;
    }

    int width = x2 - x1;
    int height = y2 - y1;

    return new Rectangle(x1, y1, width, height);
  }

  /**
   * Crops a given image with the given crop filter.
   *
   * @param image       the image to be cropped
   * @param filter      the filter determining whether a pixel is blank or not
   * @param initialCrop If not null, specifies a rectangle which contains an initial
   *                    crop to continue. This can be used to crop an image where you already
   *                    know about margins in the image
   * @param imageType   the type of {@link BufferedImage} to create, or -1 to use the type of the original image
   * @return a cropped version of the source image, or null if the whole image was blank
   *     and cropping completely removed everything
   */
  @Nullable
  public static BufferedImage crop(@Nullable BufferedImage image, @NotNull CropFilter filter, @Nullable Rectangle initialCrop,
                                   int imageType) {
    if (image == null) {
      return null;
    }

    Rectangle cropBounds = getCropBounds(image, filter, initialCrop);
    if (cropBounds == null) {
      return null;
    }

    return getCroppedImage(image, cropBounds, imageType);
  }

  /**
   * Returns a given image cropped by the given rectangle. The original image is preserved.
   *
   * @param image the image to be cropped
   * @param cropBounds defines the part of the original image that is returned
   * @param imageType the type of {@link BufferedImage} to create, or -1 to use the type of the original image
   * @return the part of the original image located inside the {@code cropBounds} rectangle
   */
  @NotNull
  public static BufferedImage getCroppedImage(@NotNull BufferedImage image, @NotNull Rectangle cropBounds, int imageType) {
    int x1 = cropBounds.x;
    int y1 = cropBounds.y;
    int width = cropBounds.width;
    int height = cropBounds.height;
    int x2 = x1 + width;
    int y2 = y1 + height;

    // Now extract the sub-image.
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
   * Returns true if at least one pixel in the image is semi-transparent (alpha != 255)
   *
   * @param image the image to check
   * @return true if it has one or more non-opaque pixels
   */
  public static boolean isNonOpaque(@NotNull BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int rgb = image.getRGB(x, y);
        if (((rgb & 0xFF000000) ^ 0xFF000000) != 0) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Clips the image by a circle. The circle has the diameter equal to the largest dimension of
   * the image and is positioned so that it is touching the top and the left edges of the image.
   * The area outside the circle is filled with backgroundColor, or left transparent if
   * backgroundColor is null.
   */
  public static @NotNull BufferedImage circularClip(@NotNull BufferedImage image, @Nullable Color backgroundColor) {
    BufferedImage mask = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = mask.createGraphics();
    applyQualityRenderingHints(g2);
    double diameter = max(image.getWidth(), image.getHeight());
    g2.fill(new Area(new Ellipse2D.Double(0, 0, diameter, diameter)));
    g2.dispose();
    BufferedImage shapedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    g2 = shapedImage.createGraphics();
    applyQualityRenderingHints(g2);
    g2.drawImage(image, 0, 0, null);
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_IN));
    g2.drawImage(mask, 0, 0, null);
    if (backgroundColor != null) {
      g2.setColor(backgroundColor);
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OVER));
      g2.fillRect(0, 0, image.getWidth(), image.getHeight());
    }
    g2.dispose();
    return shapedImage;
  }

  /**
   * Clips the image by the ellipse inscribed into the image. The area outside the ellipse is filled
   * with backgroundColor, or left transparent if backgroundColor is null.
   */
  public static @NotNull BufferedImage ellipticalClip(@NotNull BufferedImage image, @Nullable Color backgroundColor) {
    BufferedImage mask = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = mask.createGraphics();
    applyQualityRenderingHints(g2);
    g2.fill(new Area(new Ellipse2D.Double(0, 0, image.getWidth(), image.getHeight())));
    g2.dispose();
    BufferedImage shapedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    g2 = shapedImage.createGraphics();
    applyQualityRenderingHints(g2);
    g2.drawImage(image, 0, 0, null);
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_IN));
    g2.drawImage(mask, 0, 0, null);
    if (backgroundColor != null) {
      g2.setColor(backgroundColor);
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OVER));
      g2.fillRect(0, 0, image.getWidth(), image.getHeight());
    }
    g2.dispose();
    return shapedImage;
  }

  /**
   * Interface implemented by cropping functions that determine whether a pixel should be cropped or not.
   */
  public interface CropFilter {
    /**
     * Returns true if the pixel should be cropped.
     *
     * @param image the image containing the pixel in question
     * @param x     the x position of the pixel
     * @param y     the y position of the pixel
     * @return true if the pixel should be cropped (for example, is blank)
     */
    boolean crop(BufferedImage image, int x, int y);
  }

  /**
   * Utility function to convert from an Icon to a BufferedImage.
   */
  public static BufferedImage iconToImage(Icon icon) {
    if (icon instanceof ImageIcon) {
      return ImageUtil.toBufferedImage(((ImageIcon)icon).getImage());
    }
    int w = icon.getIconWidth();
    int h = icon.getIconHeight();
    BufferedImage image = ImageUtil.createImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
    Graphics2D g = image.createGraphics();
    icon.paintIcon(null, g, 0, 0);
    g.dispose();
    return image;
  }
}
