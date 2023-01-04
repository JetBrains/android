/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters;

import com.google.common.collect.ImmutableMap;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BitmapDecoder {
  enum PixelFormat {
    ARGB_8888,
    RGB_565,
    ALPHA_8
  }

  public interface BitmapDataProvider {
    @Nullable
    PixelFormat getBitmapConfigName();

    @Nullable
    Dimension getDimension();

    @Nullable
    byte[] getPixelBytes(@NotNull Dimension size);
  }

  private interface BitmapExtractor {
    BufferedImage getImage(int w, int h, byte[] data);
  }

  private static final Map<PixelFormat, BitmapExtractor> SUPPORTED_FORMATS = ImmutableMap.of(
    PixelFormat.ARGB_8888, new ARGB8888_BitmapExtractor(),
    PixelFormat.RGB_565, new RGB565_BitmapExtractor(),
    PixelFormat.ALPHA_8, new ALPHA8_BitmapExtractor());

  @Nullable
  public static BufferedImage getBitmap(@NotNull BitmapDataProvider dataProvider) {
    PixelFormat config = dataProvider.getBitmapConfigName();
    if (config == null) {
      return null;
    }

    BitmapExtractor bitmapExtractor = SUPPORTED_FORMATS.get(config);
    if (bitmapExtractor == null) {
      return null;
    }

    Dimension size = dataProvider.getDimension();
    if (size == null) {
      return null;
    }

    return bitmapExtractor.getImage(size.width, size.height, dataProvider.getPixelBytes(size));
  }

  private static class ARGB8888_BitmapExtractor implements BitmapExtractor {
    @Override
    public BufferedImage getImage(int width, int height, byte[] rgba) {
      @SuppressWarnings("UndesirableClassUsage")
      BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < height; y++) {
        int stride = y * width;
        for (int x = 0; x < width; x++) {
          int i = (stride + x) * 4;
          long argb = 0;
          argb |= ((long) rgba[i    ] & 0xff) << 16; // r
          argb |= ((long) rgba[i + 1] & 0xff) << 8;  // g
          argb |= ((long) rgba[i + 2] & 0xff);       // b
          argb |= ((long) rgba[i + 3] & 0xff) << 24; // a
          bufferedImage.setRGB(x, y, (int) (argb & 0xffffffffL));
        }
      }

      return bufferedImage;
    }
  }

  private static class RGB565_BitmapExtractor implements BitmapExtractor {
    @Override
    public BufferedImage getImage(int width, int height, byte[] rgb) {
      int bytesPerPixel = 2;

      @SuppressWarnings("UndesirableClassUsage")
      BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < height; y++) {
        int stride = y * width;
        for (int x = 0; x < width; x++) {
          int index = (stride + x) * bytesPerPixel;
          int value = (rgb[index] & 0x00ff) | (rgb[index + 1] << 8) & 0xff00;
          // RGB565 to RGB888
          // Multiply by 255/31 to convert from 5 bits (31 max) to 8 bits (255)
          int r = ((value >>> 11) & 0x1f) * 255 / 31;
          int g = ((value >>> 5) & 0x3f) * 255 / 63;
          int b = ((value) & 0x1f) * 255 / 31;
          int a = 0xFF;
          int argb = a << 24 | r << 16 | g << 8 | b;
          bufferedImage.setRGB(x, y, argb);
        }
      }

      return bufferedImage;
    }
  }

  private static class ALPHA8_BitmapExtractor implements BitmapExtractor {
    @Override
    public BufferedImage getImage(int width, int height, byte[] rgb) {
      //noinspection UndesirableClassUsage
      BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < height; y++) {
        int stride = y * width;
        for (int x = 0; x < width; x++) {
          int index = stride + x;
          int value = rgb[index];
          int argb = value << 24 | 0xff << 16 | 0xff << 8 | 0xff;
          bufferedImage.setRGB(x, y, argb);
        }
      }

      return bufferedImage;
    }
  }
}
