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

import com.android.utils.concurrency.CacheUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Cache of AAR resource repositories. This class is thread-safe.
 */
public final class AarResourceRepositoryCache {
  private final Cache<File, AarProtoResourceRepository> myProtoRepositories = CacheBuilder.newBuilder().softValues().build();
  private final Cache<File, AarSourceResourceRepository> mySourceRepositories = CacheBuilder.newBuilder().softValues().build();

  /**
   * Returns the cache.
   */
  public static AarResourceRepositoryCache getInstance() {
    return ServiceManager.getService(AarResourceRepositoryCache.class);
  }

  /**
   * Returns a cached or a newly created source resource repository.
   *
   * @param aarDirectory the directory containing unpacked contents of an AAR
   * @param libraryName the name of the library
   * @return the resource repository
   */
  @NotNull
  public AarSourceResourceRepository getSourceRepository(@NotNull File aarDirectory, @Nullable String libraryName) {
    return getRepository(aarDirectory,
                         libraryName,
                         mySourceRepositories,
                         () -> AarSourceResourceRepository.create(aarDirectory, libraryName));
  }

  /**
   * Returns a cached or a newly created proto resource repository.
   *
   * @param resApkFile the aapt static library file
   * @param libraryName the name of the library
   * @return the resource repository
   */
  @NotNull
  public AarProtoResourceRepository getProtoRepository(@NotNull File resApkFile, @Nullable String libraryName) {
    return getRepository(resApkFile,
                         libraryName,
                         myProtoRepositories,
                         () -> AarProtoResourceRepository.createProtoRepository(resApkFile, libraryName));
  }

  @NotNull
  private static <T extends AarSourceResourceRepository> T getRepository(@NotNull File file,
                                                                         @Nullable String libraryName,
                                                                         @NotNull Cache<File, T> cache,
                                                                         @NotNull Callable<T> factory) {
    T aarRepository = CacheUtils.getAndUnwrap(cache, file, factory);

    if (!Objects.equals(libraryName, aarRepository.getLibraryName())) {
      assert false : "Library name mismatch: " + libraryName + " vs " + aarRepository.getLibraryName();
      Logger logger = Logger.getInstance(AarResourceRepositoryCache.class);
      logger.error(new Exception("Library name mismatch: " + libraryName + " vs " + aarRepository.getLibraryName()));
    }

    return aarRepository;
  }

  public void remove(@NotNull File aarDirectory) {
    myProtoRepositories.invalidate(aarDirectory);
    mySourceRepositories.invalidate(aarDirectory);
  }

  public void clear() {
    myProtoRepositories.invalidateAll();
    mySourceRepositories.invalidateAll();
  }

  private AarResourceRepositoryCache() {}
}
