/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.idea.npw.assetstudio;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * An implementation of {@link GraphicGeneratorContext}, responsible for generating assets which
 * are used by our various AssetStudio wizard classes.
 */
public final class AssetStudioGraphicGeneratorContext implements GraphicGeneratorContext {

  private static Cache<String, BufferedImage> ourImageCache = CacheBuilder.newBuilder().build();

  private static Logger getLog() {
    return Logger.getInstance(AssetStudioGraphicGeneratorContext.class);
  }

  @NotNull
  private static BufferedImage getStencilImage(@NotNull String path) throws IOException {
    BufferedImage image = GraphicGenerator.getStencilImage(path);
    if (image == null) {
      image = AssetStudioUtils.createDummyImage();
    }

    return image;
  }

  @Override
  public BufferedImage loadImageResource(@NotNull final String path) {
    try {
      return ourImageCache.get(path, new Callable<BufferedImage>() {
        @Override
        public BufferedImage call() throws Exception {
          return getStencilImage(path);
        }
      });
    }
    catch (ExecutionException e) {
      getLog().error(e);
      return null;
    }
  }
}
