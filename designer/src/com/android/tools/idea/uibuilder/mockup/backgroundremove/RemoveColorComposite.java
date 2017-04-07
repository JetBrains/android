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

import java.awt.*;
import java.awt.image.*;

/**
 * <p>
 * Composite that make transparent the pixels which value is close
 * to the one set by {@link #setRemoveColor(int)}.
 * </p>
 *
 * <p>
 * The the output raster will result in a image showing only the colors different from the set one.
 * This can be used for example to remove the background from an image.
 * </p>
 *
 * <p>
 * The color to extract has to be set with {@link #setRemoveColor(int)}.
 * To adjust the threshold of which color are considered similar, use {@link #setThreshold(double)}.
 * </p>
 *
 * <p>
 * The Threshold is the maximum euclidean distance in the RGB color space for two color to be considered similar.
 * </p>
 *
 * <p>
 * <b> Only works for Raster whose {@link DataBuffer} are of type {@link DataBuffer#TYPE_INT}</b>
 * </p>
 */
public class RemoveColorComposite implements Composite, CompositeContext {

  private double myThreshold;
  private int[] myRemoveColor = new int[3];
  private int ox, oy;
  private boolean myIsAreaOnly;

  public void setRemoveColor(int removeColor) {
   myRemoveColor[RemoveAlgorithms.R] = (removeColor >> 16) & 0xFF;
   myRemoveColor[RemoveAlgorithms.G] = (removeColor >> 8) & 0xFF;
   myRemoveColor[RemoveAlgorithms.B] = (removeColor) & 0xFF;
  }

  /**
   * Set the the maximum euclidean distance in the RGB color space for
   * two color to be considered similar.
   *
   * @param threshold a double value from 0 to 1 representing the threshold. <br/>
   *                  0 means exactly the same color as {@link #myRemoveColor}, <br/>
   *                  1 means all the color in the raster <br/>
   */
  public void setThreshold(double threshold) {
    myThreshold = threshold < 0 ? -1 : Math.max(0, Math.min(1, threshold)) * 255 * 3;
  }

  /**
   * Set if the removal should happen only on the origin area using a flood-fill algorithm
   * or should happen one evey similar colors in the whole image.
   *
   * @param areaOnly if true, the removal will happen only on the origin area else
   *                 the removal will happen on the whole image.
   * @see RemoveColorComposite#setOriginPixel(int, int)
   */
  public void setAreaOnly(boolean areaOnly) {
    myIsAreaOnly = areaOnly;
  }

  /**
   * Set the origin point for the flood fill algorithm is {@link #setAreaOnly(boolean)} is true.
   *
   * @param x The x coordinate of the point. If x < 0, it will be set to 0, if it is greater
   *          than the width of the raster at composition time,
   *          and {@link #setAreaOnly(boolean)} is set to true, no removal will happen.
   * @param y The y coordinate of the point. If y < 0, it will be set to 0, if it is greater
   *          than the height of the raster at composition time,
   *          and {@link #setAreaOnly(boolean)} is set to true, no removal will happen.
   */
  public void setOriginPixel(int x, int y) {
    ox = Math.max(0, x);
    oy = Math.max(0, y);
  }

  @Override
  public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
    return this;
  }

  @Override
  public void dispose() {
    // Do nothing
  }

  @Override
  public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
    if (src.getSampleModel().getDataType() == DataBuffer.TYPE_INT &&
        dstIn.getSampleModel().getDataType() == DataBuffer.TYPE_INT &&
        dstOut.getSampleModel().getDataType() == DataBuffer.TYPE_INT) {

      if (myThreshold < 0) {
        // Just copy source to dest
        dstOut.setDataElements(0, 0, src);
        return;
      }

      if (myIsAreaOnly) {
        RemoveAlgorithms.floodFill(src, dstIn, dstOut, ox, oy, myRemoveColor, myThreshold);
      }
      else {
        RemoveAlgorithms.whole(src, dstIn, dstOut, myRemoveColor, myThreshold);
      }
    }
  }
}