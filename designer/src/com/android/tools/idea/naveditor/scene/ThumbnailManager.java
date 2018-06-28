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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.reference.SoftReference;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetScopedService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Creates and caches preview images of screens in the nav editor.
 */
public class ThumbnailManager extends AndroidFacetScopedService {
  private static final Key<ThumbnailManager> KEY = Key.create(ThumbnailManager.class.getName());

  private final Table<VirtualFile, Configuration, SoftReference<BufferedImage>> myImages = HashBasedTable.create();
  private final Table<VirtualFile, Configuration, Long> myRenderVersions = HashBasedTable.create();
  private final Table<VirtualFile, Configuration, Long> myRenderModStamps = HashBasedTable.create();
  private final LocalResourceRepository myResourceRepository;

  @GuardedBy("DISPOSAL_LOCK")
  private final Map<VirtualFile, CompletableFuture<BufferedImage>> myPendingFutures = new HashMap<>();

  @GuardedBy("DISPOSAL_LOCK")
  private boolean myDisposed;

  private final Object DISPOSAL_LOCK = new Object();

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
    myResourceRepository = ResourceRepositoryManager.getAppResources(facet);
  }

  @Override
  protected void onDispose() {
    CompletableFuture[] futures;
    synchronized (DISPOSAL_LOCK) {
      myDisposed = true;
      futures = myPendingFutures.values().toArray(new CompletableFuture[0]);
      myPendingFutures.clear();
    }
    try {
      CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
    }
    catch (Exception e) {
      // We do not care about these exceptions since we are disposing anyway
    }
    super.onDispose();
  }

  @Nullable
  public CompletableFuture<BufferedImage> getThumbnail(@NotNull XmlFile xmlFile, @NotNull Configuration configuration) {
    VirtualFile file = xmlFile.getVirtualFile();
    SoftReference<BufferedImage> cachedReference = myImages.get(file, configuration);
    BufferedImage cached = cachedReference != null ? cachedReference.get() : null;
    if (cached != null
        && myRenderVersions.get(file, configuration) == myResourceRepository.getModificationCount()
        && myRenderModStamps.get(file, configuration) == file.getModificationStamp()) {
      return CompletableFuture.completedFuture(cached);
    }

    CompletableFuture<BufferedImage> result = new CompletableFuture<>();
    synchronized (DISPOSAL_LOCK) {
      if (myDisposed) {
        return null;
      }
      CompletableFuture<BufferedImage> inProgress = myPendingFutures.get(file);
      if (inProgress != null) {
        return inProgress;
      }
      myPendingFutures.put(file, result);
    }

    // TODO we run in a separate thread because task.render() currently isn't asynchronous
    // if inflate() (which is itself synchronous) hasn't already been called.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        synchronized (DISPOSAL_LOCK) {
          // We might have been disposed while waiting to run
          if (myDisposed) {
            result.complete(null);
            return;
          }
        }
        try {
          result.complete(getImage(xmlFile, file, configuration));
        }
        catch (Exception e) {
          result.completeExceptionally(e);
        }
        finally {
          synchronized (DISPOSAL_LOCK) {
            myPendingFutures.remove(file);
          }
        }
      }
      catch (Throwable t) {
        result.completeExceptionally(t);
        synchronized (DISPOSAL_LOCK) {
          myPendingFutures.remove(file);
        }
      }
    });
    return result;
  }

  @Nullable
  private BufferedImage getImage(@NotNull XmlFile xmlFile, @NotNull VirtualFile file, @NotNull Configuration configuration)
    throws InterruptedException, ExecutionException {
    RenderService renderService = RenderService.getInstance(getModule().getProject());
    RenderTask task = createTask(getFacet(), xmlFile, configuration, renderService);
    ListenableFuture<RenderResult> renderResult = null;
    if (task != null) {
      renderResult = task.render();
    }
    BufferedImage image = null;
    if (renderResult != null) {
      // This should also be done in a listener if task.render() were actually async.
      image = renderResult.get().getRenderedImage().getCopy();
      myImages.put(file, configuration, new SoftReference<>(image));
      myRenderVersions.put(file, configuration, myResourceRepository.getModificationCount());
      myRenderModStamps.put(file, configuration, file.getModificationStamp());
    }
    return image;
  }

  @Nullable
  protected RenderTask createTask(@NotNull AndroidFacet facet,
                                  @NotNull XmlFile file,
                                  @NotNull Configuration configuration,
                                  RenderService renderService) {
    RenderTask task = renderService.createTask(facet, file, configuration);
    if (task != null) {
      task.setDecorations(false);
    }
    return task;
  }

  @Override
  protected void onServiceDisposal(@NotNull AndroidFacet facet) {

  }
}
