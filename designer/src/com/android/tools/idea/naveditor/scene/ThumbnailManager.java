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
package com.android.tools.idea.naveditor.scene;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.res.AppResourceRepository;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.util.Key;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetScopedService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Creates and caches preview images of screens in the nav editor.
 */
public class ThumbnailManager extends AndroidFacetScopedService {
  private static final Key<ThumbnailManager> KEY = Key.create(ThumbnailManager.class.getName());

  private final Table<XmlFile, Configuration, ImagePool.Image> myImages = HashBasedTable.create();
  private final Table<XmlFile, Configuration, Long> myRenderVersions = HashBasedTable.create();
  private final Table<XmlFile, Configuration, Long> myRenderModStamps = HashBasedTable.create();
  private final AppResourceRepository myResourceRepository;

  @NotNull
  public static ThumbnailManager getInstance(@NotNull AndroidFacet facet) {
    ThumbnailManager manager = facet.getUserData(KEY);
    if (manager == null) {
      manager = new ThumbnailManager(facet);
      setInstance(facet, manager);
    }
    return manager;
  }

  @VisibleForTesting
  public static void setInstance(@NotNull AndroidFacet facet, @Nullable ThumbnailManager manager) {
    facet.putUserData(KEY, manager);
  }

  protected ThumbnailManager(@NotNull AndroidFacet facet) {
    super(facet);
    myResourceRepository = AppResourceRepository.getOrCreateInstance(facet);
  }

  @Nullable
  public CompletableFuture<ImagePool.Image> getThumbnail(@NotNull XmlFile file, @NotNull Configuration configuration) {
    ImagePool.Image cached = myImages.get(file, configuration);
    long version = myResourceRepository.getModificationCount();
    long modStamp = file.getModificationStamp();
    if (cached != null
        && myRenderVersions.get(file, configuration) == version
        && myRenderModStamps.get(file, configuration) == file.getModificationStamp()) {
      return CompletableFuture.completedFuture(cached);
    }

    RenderService renderService = RenderService.getInstance(getFacet());
    RenderLogger logger = renderService.createLogger();
    RenderTask task = createTask(file, configuration, renderService, logger);
    CompletableFuture<ImagePool.Image> result = new CompletableFuture<>();
    if (task != null) {
      ListenableFuture<RenderResult> renderResult = task.render();
      renderResult.addListener(() -> {
        try {
          ImagePool.Image image = renderResult.get().getRenderedImage();
          myImages.put(file, configuration, image);
          myRenderVersions.put(file, configuration, version);
          myRenderModStamps.put(file, configuration, modStamp);
          result.complete(image);
        }
        catch (InterruptedException | ExecutionException e) {
          result.completeExceptionally(e);
        }
      }, PooledThreadExecutor.INSTANCE);
    }
    else {
      result.complete(null);
    }
    return result;
  }

  @Nullable
  protected RenderTask createTask(@NotNull XmlFile file,
                                  @NotNull Configuration configuration,
                                  RenderService renderService, RenderLogger logger) {
    RenderTask task = renderService.createTask(file, configuration, logger);
    if (task != null) {
      task.setDecorations(false);
    }
    return task;
  }

  @Override
  protected void onServiceDisposal(@NotNull AndroidFacet facet) {

  }
}
