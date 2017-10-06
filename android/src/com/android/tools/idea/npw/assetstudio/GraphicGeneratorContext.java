/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.Pair;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * The context used for graphic generation.
 */
public class GraphicGeneratorContext implements Disposable {
  private final Cache<Object, ListenableFuture<BufferedImage>> myImageCache;
  private final DrawableRenderer myDrawableRenderer;

  /**
   * @param maxCacheSize the maximum size of the image cache
   */
  public GraphicGeneratorContext(int maxCacheSize) {
    this(maxCacheSize, null);
  }

  /**
   * @param maxCacheSize the maximum size of the image cache
   * @param drawableRenderer the renderer used to convert XML drawables into raster images
   */
  public GraphicGeneratorContext(int maxCacheSize, @Nullable DrawableRenderer drawableRenderer) {
    myImageCache = CacheBuilder.newBuilder().maximumSize(maxCacheSize).build();
    myDrawableRenderer = drawableRenderer;
  }

  @Override
  public void dispose() {
    if (myDrawableRenderer != null) {
      myDrawableRenderer.dispose();
    }
  }

  /**
   * Returns the image from the cache or creates a new image, puts it in the cache, and returns it.
   *
   * @param key the key for the cache lookup
   * @param creator the image creator that is called if the image was not found in the cache
   * @return the cached or the newly created image
   */
  @NotNull
  public final ListenableFuture<BufferedImage> getFromCacheOrCreate(@NotNull Object key,
                                                                    @NotNull Callable<ListenableFuture<BufferedImage>> creator) {
    try {
      return myImageCache.get(key, creator);
    }
    catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause == null) {
        cause = e;
      }
      getLog().error(cause);
      return Futures.immediateFailedFuture(cause);
    }
  }

  /**
   * Loads the given image resource, as requested by the graphic generator.
   *
   * @param path The path to the resource, relative to the general "resources" path
   * @return The loaded image resource, or null if there was an error
   */
  @Nullable
  public BufferedImage loadImageResource(@NonNull String path) {
    try {
      ListenableFuture<BufferedImage> imageFuture = getFromCacheOrCreate(path, () -> getStencilImage(path));
      return imageFuture.get();
    }
    catch (ExecutionException | InterruptedException e) {
      getLog().error(e);
      return null;
    }
  }

  /**
   * Renders the given drawable to a raster image.
   *
   * @param xmlDrawableText the text of an XML drawable
   * @param size the size of the raster image
   * @return the raster image that is created asynchronously
   * @throws IllegalStateException if a drawable renderer was not provided to the constructor
   */
  @NonNull
  public ListenableFuture<BufferedImage> renderDrawable(@NonNull String xmlDrawableText, @NonNull Dimension size) {
    if (myDrawableRenderer == null) {
      throw new IllegalStateException("Cannot render a drawable without a renderer");
    }
    Pair<String, Dimension> key = Pair.of(xmlDrawableText, size);
    Callable<ListenableFuture<BufferedImage>> renderer = () -> myDrawableRenderer.renderDrawable(xmlDrawableText, size);
    return getFromCacheOrCreate(key, renderer);
  }

  @NotNull
  private static ListenableFuture<BufferedImage> getStencilImage(@NotNull String path) throws IOException {
    BufferedImage image = GraphicGenerator.getStencilImage(path);
    if (image == null) {
      image = AssetStudioUtils.createDummyImage();
    }

    return Futures.immediateFuture(image);
  }

  @NotNull
  private Logger getLog() {
    return Logger.getInstance(getClass());
  }

  /**
   * Interface to be implemented by renderers of XML drawables.
   */
  public interface DrawableRenderer extends Disposable {
    public ListenableFuture<BufferedImage> renderDrawable(@NonNull String xmlDrawableText, @NonNull Dimension size);
  }
}
