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
package com.android.tools.rendering.imagepool;

import static com.android.tools.rendering.imagepool.ImagePoolUtil.stackTraceToAssertionString;

import com.intellij.openapi.diagnostic.Logger;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NonPooledImage implements ImagePool.Image, DisposableImage {
  private static final Logger LOG = Logger.getInstance(NonPooledImage.class);
  // Track dispose call when assertions are enabled
  private static final boolean ourTrackDisposeCall = NonPooledImage.class.desiredAssertionStatus();

  private BufferedImage myImage;
  private final int myWidth;
  private final int myHeight;
  /**
   * If we are tracking the dispose calls, this will contain the stack trace of the first caller to dispose
   */
  private StackTraceElement[] myDisposeStackTrace = null;

  private NonPooledImage(@NotNull BufferedImage image) {
    myImage = image;
    myHeight = myImage.getHeight();
    myWidth = myImage.getWidth();
  }

  private void assertIfDisposed() {
    if (myDisposeStackTrace != null) {
      LOG.warn("Accessing already disposed image\nDispose trace: \n" + stackTraceToAssertionString(myDisposeStackTrace),
               new Throwable());
    }
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
    assertIfDisposed();
    g.drawImage(myImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
  }

  @Override
  public void paint(Consumer<Graphics2D> command) {
    assertIfDisposed();
    command.accept(myImage.createGraphics());
  }

  @Nullable
  @Override
  public BufferedImage getCopy(@Nullable GraphicsConfiguration gc, int x, int y, int w, int h) {
    assertIfDisposed();
    if (x == 0 && y == 0 && w == getWidth() && h == getHeight()) {
      return copy(myImage);
    }
    return copy(myImage.getSubimage(x, y, w, h));
  }

  @Override
  public void dispose() {
    if (ourTrackDisposeCall) {
      myDisposeStackTrace = Thread.currentThread().getStackTrace();
    }
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
