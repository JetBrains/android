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
import java.awt.image.ImageObserver;
import java.util.function.Consumer;

/**
 * Class that offers a pool of {@link BufferedImage}s. The returned {@link Image} do not offer a direct access
 * to the underlying {@link BufferedImage} to avoid clients holding references to it.
 * Once the {@link Image} is not being referenced anymore, it will be automatically returned to the pool.
 */
@SuppressWarnings("ALL")
public interface ImagePool {
  public static final Image NULL_POOLED_IMAGE = new Image() {

    @Override
    public int getWidth() {
      return 0;
    }

    @Override
    public int getHeight() {
      return 0;
    }

    @Override
    public void drawImageTo(@NotNull Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {}

    @Override
    public void paint(Consumer<Graphics2D> command) {}

    @Override
    @Nullable
    public BufferedImage getCopy(@Nullable GraphicsConfiguration gc, int x, int y, int w, int h) {
      return null;
    }

    @Override
    public void dispose() {}
  };

  /**
   * Returns a new image of width w and height h.
   */
  @NotNull
  public Image create(final int w, final int h, final int type);

  /**
   * Returns a pooled image with a copy of the passed {@link BufferedImage}
   */
  @NotNull
  public Image copyOf(@Nullable BufferedImage origin);

  @Nullable
  Stats getStats();

  /**
   * Disposes the image pool
   */
  public void dispose();

  /**
   * Interface for bucket specific stats
   */
  interface BucketStats {
    int getMinWidth();
    int getMinHeight();
    int maxSize();

    /**
     * Returns the last time the bucket was accessed in milliseconds.
     */
    long getLastAccessTimeMs();

    /**
     * Returns the number of times this bucket contained an image that was reused.
     */
    long bucketHits();

    /**
     * Returns the number of times this bucket was empty when an image from it was needed.
     */
    long bucketMisses();

    /**
     * Returns the number of times we had an image that was freed but could not be returned to this bucket.
     */
    long bucketWasFull();

    /**
     * Returns the number of times we had an image that was returned to this bucket.
     */
    long imageWasReturned();
  }

  interface Stats {
    long totalBytesAllocated();

    long totalBytesInUse();

    BucketStats[] getBucketStats();
  }

  /**
   * Interface that represents an image from the pool. Clients can not access the inner BufferedImage directly and
   * can only get copies of it.
   */
  public interface Image {
    /**
     * Returns the width of the image
     */
    int getWidth();

    /**
     * Returns the height of the image
     */
    int getHeight();

    /**
     * Draws the current image to the given {@link Graphics} context.
     * See {@link Graphics#drawImage(java.awt.Image, int, int, int, int, int, int, int, int, ImageObserver)}
     */
    void drawImageTo(@NotNull Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2);

    /**
     * Allows painting into the {@link Image}. The passed {@link Graphics2D} context will be disposed right after this call finishes so
     * do not keep a reference to it.
     */
    void paint(Consumer<Graphics2D> command);

    /**
     * Draws the current image to the given {@link Graphics} context.
     * See {@link Graphics#drawImage(java.awt.Image, int, int, int, int, ImageObserver)}
     */
    default void drawImageTo(@NotNull Graphics g, int x, int y, int w, int h) {
      drawImageTo(g, x, y, x + w, y + h, 0, 0, getWidth(), getHeight());
    }

    /**
     * Draws the current image to the given {@link BufferedImage}. If the passed destination buffer size does not match the pooled image
     * width an height, the image will be scaled.
     */
    default void drawImageTo(@NotNull BufferedImage destination) {
      Graphics g = destination.getGraphics();
      try {
        drawImageTo(g, 0, 0, destination.getWidth(), destination.getHeight());
      }
      finally {
        g.dispose();
      }
    }

    /**
     * Returns a {@link BufferedImage} with a copy of a sub-image of the pooled image. If you pass the
     * optional {@link GraphicsConfiguration}, the returned copy will be compatible with that configuration.
     */
    @SuppressWarnings("SameParameterValue")
    @Nullable
    BufferedImage getCopy(@Nullable GraphicsConfiguration gc, int x, int y, int w, int h);

    /**
     * Returns a {@link BufferedImage} with a copy of a sub-image of the pooled image.
     */
    @Nullable
    default BufferedImage getCopy(int x, int y, int w, int h) {
      return getCopy(null, x, y, w, h);
    }

    /**
     * Returns a {@link BufferedImage} with a copy of the pooled image. The copy will be compatible with the given
     * {@link GraphicsConfiguration}.
     * If the original image is large, and you plan to paint to screen, use this method to obtain the copy.
     */
    @Nullable
    default BufferedImage getCopy(@NotNull GraphicsConfiguration gc) {
      return getCopy(gc, 0, 0, getWidth(), getHeight());
    }

    /**
     * Returns a {@link BufferedImage} with a copy of the pooled image
     */
    @Nullable
    default BufferedImage getCopy() {
      return getCopy(null, 0, 0, getWidth(), getHeight());
    }

    /**
     * Manually disposes the current image. After calling this method, the image can not be used anymore.
     * <p>
     * This method does not need to be called directly as the images will be eventually collected anyway. However, using this method, you can
     * speed up the collection process to avoid generating extra images.
     */
    void dispose();
  }
}
