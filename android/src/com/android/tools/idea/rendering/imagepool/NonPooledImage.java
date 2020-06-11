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
package com.android.tools.idea.rendering.imagepool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.function.Consumer;

class NonPooledImage implements ImagePool.Image {
  private BufferedImage myImage;
  private final int myWidth;
  private final int myHeight;

  private NonPooledImage(@NotNull BufferedImage image) {
    myImage = image;
    myHeight = myImage.getHeight();
    myWidth = myImage.getWidth();
  }

  @Override
  public int getWidth() {
    return myWidth;
  }

  @Override
  public int getHeight() {
    return myHeight;
  }

  @Override
  public void drawImageTo(@NotNull Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
    assert myImage != null : "Image already disposed";
    g.drawImage(myImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
  }

  @Override
  public void paint(Consumer<Graphics2D> command) {
    assert myImage != null : "Image already disposed";
    command.accept(myImage.createGraphics());
  }

  @Nullable
  @Override
  public BufferedImage getCopy(@Nullable GraphicsConfiguration gc, int x, int y, int w, int h) {
    assert myImage != null : "Image already disposed";
    if (x == 0 && y == 0 && w == getWidth() && h == getHeight()) {
      return copy(myImage);
    }
    return copy(myImage.getSubimage(x, y, w, h));
  }

  @Override
  public void dispose() {
    myImage = null;
  }

  @Override
  public boolean isValid() {
    return myImage != null;
  }

  @NotNull
  private static BufferedImage copy(@NotNull BufferedImage originalImage) {
    WritableRaster raster = originalImage.copyData(originalImage.getRaster().createCompatibleWritableRaster());

    //noinspection UndesirableClassUsage
    return new BufferedImage(originalImage.getColorModel(),
                             raster,
                             originalImage.isAlphaPremultiplied(),
                             null);
  }

  @NotNull
  public static NonPooledImage create(int w, int h, int type) {
    return new NonPooledImage(new BufferedImage(w, h, type));
  }

  @NotNull
  static NonPooledImage copyOf(@NotNull BufferedImage image) {
    return new NonPooledImage(copy(image));
  }
}
