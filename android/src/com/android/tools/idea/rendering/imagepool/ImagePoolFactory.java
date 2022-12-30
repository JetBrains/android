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

import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory to create new instances of {@link ImagePool}. The factory currently offers another
 * method that returns an {@link ImagePool} instance that has pooling disabled.
 */
public final class ImagePoolFactory {
  private static final ImagePool NO_POOL_INSTANCE = new ImagePool() {
    @NotNull
    @Override
    public Image create(int w, int h, int type) {
      return NonPooledImage.create(w, h, type);
    }

    @NotNull
    @Override
    public Image copyOf(@Nullable BufferedImage origin) {
      return origin != null ? NonPooledImage.copyOf(origin) : ImagePool.NULL_POOLED_IMAGE;
    }

    @Nullable
    @Override
    public Stats getStats() {
      return null;
    }

    @Override
    public void dispose() {
    }
  };

  private ImagePoolFactory() {
  }

  /**
   * Creates a new {@link ImagePool} with the default settings
   */
  @NotNull
  public static ImagePool createImagePool() {
    return new ImagePoolImpl(new int[]{50, 500, 1000, 1500, 2000, 5000}, (w, h) -> (type) -> {
      // Images below 1k, do not pool
      if (w * h < 1000) {
        return 0;
      }

      return 50_000_000 / (w * h);
    });
  }

  /**
   * Returns an {@link ImagePool} instance that does not do image pooling
   */
  @NotNull
  public static ImagePool getNonPooledPool() {
    return NO_POOL_INSTANCE;
  }
}
