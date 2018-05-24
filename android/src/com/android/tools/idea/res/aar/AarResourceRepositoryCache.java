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
package com.android.tools.idea.res.aar;

import com.android.builder.model.AaptOptions;
import com.android.tools.idea.res.FileResourceRepository;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Cache of AAR resource repositories. This class is thread-safe.
 */
public final class AarResourceRepositoryCache {
  private final Cache<File, FileResourceRepository> myNamespacedCache = CacheBuilder.newBuilder().softValues().build();
  private final Cache<File, FileResourceRepository> myNonnamespacedCache = CacheBuilder.newBuilder().softValues().build();

  /**
   * Returns the cache.
   */
  public static AarResourceRepositoryCache getInstance() {
    return ServiceManager.getService(AarResourceRepositoryCache.class);
  }

  /**
   * Returns a cached or a newly created resource repository.
   *
   * @param aarDirectory the directory containing unpacked contents of an AAR
   * @param namespacing indicates whether the application is using resource namespaces or not
   * @param libraryName the name of the library
   * @return the resource repository
   */
  @NotNull
  public FileResourceRepository get(@NotNull File aarDirectory, @NotNull AaptOptions.Namespacing namespacing,
                                    @Nullable String libraryName) {
    Cache<File, FileResourceRepository> cache = namespacing == AaptOptions.Namespacing.REQUIRED ? myNamespacedCache : myNonnamespacedCache;
    try {
      FileResourceRepository aarRepository = cache.get(aarDirectory, () -> {
        FileResourceRepository repository =
            namespacing == AaptOptions.Namespacing.REQUIRED ? AarProtoResourceRepository.createIfProtoAar(aarDirectory, libraryName) : null;
        // TODO(b/74425399): Remove the fallback to FileResourceRepository when namespacing is enabled.
        if (repository == null) {
          repository = FileResourceRepository.create(aarDirectory, libraryName);
        }
        return repository;
      });
      if (!Objects.equals(libraryName, aarRepository.getLibraryName())) {
        assert false : "Library name mismatch: " + libraryName + " vs " + aarRepository.getLibraryName();
        Logger logger = Logger.getInstance(AarResourceRepositoryCache.class);
        logger.warn(new Exception("Library name mismatch: " + libraryName + " vs " + aarRepository.getLibraryName()));
      }
      return aarRepository;
    }
    catch (ExecutionException | UncheckedExecutionException e) {
      Throwables.throwIfUnchecked(e.getCause());
      throw new UncheckedExecutionException(e.getCause());
    }
  }

  public void remove(@NotNull File aarDirectory) {
    myNamespacedCache.invalidate(aarDirectory);
    myNonnamespacedCache.invalidate(aarDirectory);
  }

  public void clear() {
    myNamespacedCache.invalidateAll();
    myNonnamespacedCache.invalidateAll();
  }

  private AarResourceRepositoryCache() {}
}
