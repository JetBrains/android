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
package com.android.tools.idea.uibuilder.mockup.backgroundremove;

import com.intellij.openapi.diagnostic.Logger;

import java.awt.*;
import java.awt.image.Raster;
import java.awt.image.RasterFormatException;
import java.awt.image.WritableRaster;

/**
 * Algorithms to parse the raster and remove the provided color
 */
public final class RemoveAlgorithms {
  /**
   * Index of the red component when representing the color in a int array
   */
  static final int R = 0;
  /**
   * Index of the green component when representing the color in a int array
   */
  static final int G = 1;
  /**
   * Index of the blue component when representing the color in a int array
   */
  static final int B = 2;

  /**
   * The color are removed using a flood fill algorithm from the point defined by (ox,oy) .
   * If the point is outside the raster area, nothing happens and an error is logged.
   *
   * The flood fill algorithm is implemented using the scanline version to improve performances.
   *
   * @param src         The source Raster given by {@link CompositeContext#compose(Raster, Raster, WritableRaster)}
   * @param dstIn       The destination Raster given by {@link CompositeContext#compose(Raster, Raster, WritableRaster)}
   * @param dstOut      The destination  {@link WritableRaster} given by {@link CompositeContext#compose(Raster, Raster, WritableRaster)}
   * @param ox          X coordinate of the flood-fill origin point
   * @param oy          Y coordinate of the flood-fill origin point
   * @param removeColor The color to set transparent in the raster
   * @param threshold   The threshold such as every color whose distance from
   *                    removeColor <= threshold will be set to transparent
   */
  static void floodFill(Raster src, Raster dstIn, WritableRaster dstOut, int ox, int oy, int[] removeColor, double threshold) {
    int height = Math.min(src.getHeight(), dstIn.getHeight());
    int width = Math.min(src.getWidth(), dstIn.getWidth());
    int[] dstPixels = new int[width];
    int[] srcDownPixels = new int[width];
    int[] srcUpPixels = new int[width];
    int[] srcPixels = new int[width];
    int[] stack = new int[(width * height)];
    boolean[] visited = new boolean[width * height];
    int size = 0;
    boolean spanUp, spanDown;

    // Check if the origin point is inside the rater
    if (oy > height || ox > width) {
      Logger.getInstance(RemoveColorComposite.class).error(
        new RasterFormatException("The origin point is outside the raster area"));
    }

    // Put the origin point index in the stack
    stack[size++] = oy * width + ox % width;

    // Copy all src pixel in the output Raster since
    // the flood fill won't go through all the pixel
    dstOut.setDataElements(0, 0, src);

    // Begin the flood fill
    int nx;
    while (size > 0) {
      // Pop the pixel index from the stash
      int seed = stack[--size];
      int y = seed / width;
      int x = seed % width;

      // Load the current line inside the buffers
      dstIn.getDataElements(0, y, width, 1, dstPixels);
      src.getDataElements(0, y, width, 1, srcPixels);

      // load the lines over and below
      if (y > 0) {
        src.getDataElements(0, y - 1, width, 1, srcUpPixels);
      }
      if (y < height - 1) {
        src.getDataElements(0, y + 1, width, 1, srcDownPixels);
      }

      nx = x;
      // "Rewind" to the first pixel of the region
      while (nx >= 0 && dist(srcPixels[nx], removeColor) <= threshold) nx--;
      nx++;

      // Reset the span up and down flags
      spanUp = spanDown = false;

      // Loop on the whole line until wwe "hit" a different color
      double dist;
      while (nx < width && (dist = dist(srcPixels[nx], removeColor)) <= threshold) {

        if (!visited[y * width + nx % width]) {
          visited[y * width + nx % width] = true;
          // Set this pixel to be transparent
          double alpha;
          if (((dstPixels[nx] >> 24) & 0xFF) == 0xFF
              && dist > threshold / 2.0) {
            alpha = 0xFF * (dist / threshold);
          }
          else {
            alpha = 0;
          }
          dstPixels[nx] = (srcPixels[nx] & 0x00FFFFFF) |
                          (Math.round((float)alpha) << 24);
        }
        else {
          // If the pixel is already transparent, skip to the next
          nx++;
          continue;
        }

        // Find the distance between the current pixel an the one over it
        double distUp = dist(srcUpPixels[nx], removeColor);
        if (!spanUp && y > 0 && distUp <= threshold) {
          // If the two color are close enough and
          // If we have not already added a seed for this span
          // add the seed to the stack and set the flag to stop looking for a seed in this span
          stack[size++] = (y - 1) * width + nx % width;
          spanUp = true;
        }
        else if (spanUp && y > 0 && distUp > threshold) {
          // If the two colors are different, reset the flag
          spanUp = false;
        }

        // Find the distance between the current pixel an the one below it
        double distDown = dist(srcDownPixels[nx], removeColor);
        if (!spanDown && y < height - 1 && distDown <= threshold) {
          // If the two color are close enough and
          // If we have not already added a seed for this span
          // add the seed to the stack and set the flag to stop looking for a seed in this span
          stack[size++] = (y + 1) * width + nx % width;
          spanDown = true;
        }
        else if (spanDown && y < height - 1 && distDown > threshold) {
          // If the two colors are different, reset the flag
          spanDown = false;
        }
        nx++;
      }
      dstOut.setDataElements(0, y, width, 1, dstPixels);
    }
  }

  /**
   * The color are removed in the whole image.
   *
   * @param src         The source Raster given by {@link CompositeContext#compose(Raster, Raster, WritableRaster)}
   * @param dstIn       The destination Raster given by {@link CompositeContext#compose(Raster, Raster, WritableRaster)}
   * @param dstOut      The destination  {@link WritableRaster} given by {@link CompositeContext#compose(Raster, Raster, WritableRaster)}
   * @param removeColor The color to set transparent in the raster
   * @param threshold   The threshold such as every color whose distance from
   *                    removeColor <= threshold will be set to transparent
   */
  static void whole(Raster src, Raster dstIn, WritableRaster dstOut, int[] removeColor, double threshold) {
    int width = Math.min(src.getWidth(), dstIn.getWidth());
    int[] dstPixels = new int[width];
    int[] srcPixels = new int[width];
    for (int y = 0; y < Math.min(src.getHeight(), dstIn.getHeight()); ++y) {

      // Fill the buffer of the scanline
      dstIn.getDataElements(0, y, width, 1, dstPixels);
      src.getDataElements(0, y, width, 1, srcPixels);

      // For each pixel in the scanline
      for (int x = 0; x < width; ++x) {
        int pixel = srcPixels[x];

        // If the distance is below the threshold, make the pixel transparent
        double dist = dist(pixel, removeColor);
        int alpha;
        if (dist <= threshold) {
          if (dist > threshold / 2.0) {
            alpha = (int)Math.round(0xFF * (dist / threshold));
          }
          else {
            alpha = 0;
          }
        }
        else {
          alpha = 0xFF;
        }
        // Copy the pixel in the line buffer
        dstPixels[x] = (pixel & 0xFFFFFF) | ((alpha & 0xFF) << 24);
      }
      // Copy the new value on the output
      dstOut.setDataElements(0, y, width, 1, dstPixels);
    }
  }

  /**
   * Compute the euclidean distance in the RGB space between colorA and colorB
   *
   * @param colorA The first color in int representation RRGGBB
   * @param colorB The second color in int array representation with
   *               the color index as defined by {@link #R}, {@link #G}, {@link #B}
   * @return the euclidean distance in the RGB space between colorA and colorB
   */
  private static double dist(int colorA, int[] colorB) {
    return Math.abs(colorB[R] - ((colorA >> 16) & 0xFF))
           + Math.abs(colorB[G] - ((colorA >> 8) & 0xFF))
           + Math.abs(colorB[B] - (colorA & 0xFF));
  }
}
