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

import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.ResourceFolder;
import com.android.utils.concurrency.CacheUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cache of AAR resource repositories. This class is thread-safe.
 */
public final class AarResourceRepositoryCache {
  private final Cache<Path, AarProtoResourceRepository> myProtoRepositories = CacheBuilder.newBuilder().softValues().build();
  private final Cache<ResourceFolder, AarSourceResourceRepository> mySourceRepositories = CacheBuilder.newBuilder().softValues().build();

  /**
   * Returns the cache.
   */
  public static AarResourceRepositoryCache getInstance() {
    return ServiceManager.getService(AarResourceRepositoryCache.class);
  }

  /**
   * Returns a cached or a newly created source resource repository.
   *
   * @param library AAR library
   * @throws IllegalArgumentException if {@code library} doesn't contain resources or its resource folder doesn't point
   *     to a local file system directory
   * @return the resource repository
   */
  @NotNull
  public AarSourceResourceRepository getSourceRepository(@NotNull ExternalLibrary library) {
    ResourceFolder resFolder = library.getResFolder();
    String libraryName = library.getAddress();
    if (resFolder == null) {
      throw new IllegalArgumentException("No resource for " + libraryName);
    }

    File resourceDirectory = resFolder.getRoot().toFile();
    if (resourceDirectory == null) {
      throw new IllegalArgumentException("Cannot find resource directory " + resFolder.getRoot() + " for " + libraryName);
    }
    return getRepository(resFolder, libraryName, mySourceRepositories, () -> AarSourceResourceRepository.create(resFolder, libraryName));
  }

  /**
   * Returns a cached or a newly created proto resource repository.
   *
   * @param library aar library
   * @throws IllegalArgumentException if {@code library} doesn't contain res.apk or its res.apk isn't a file on the local file system
   * @return the resource repository
   */
  @NotNull
  public AarProtoResourceRepository getProtoRepository(@NotNull ExternalLibrary library) {
    PathString resApkPath = library.getResApkFile();
    String libraryName = library.getAddress();
    if (resApkPath == null) {
      throw new IllegalArgumentException("No res.apk for " + libraryName);
    }

    Path resApkFile = resApkPath.toPath();
    if (resApkFile == null) {
      throw new IllegalArgumentException("Cannot find " + resApkPath + " for " + libraryName);
    }

    return getRepository(resApkFile, libraryName, myProtoRepositories, () -> AarProtoResourceRepository.create(resApkFile, libraryName));
  }

  @NotNull
  private static <K, T extends AarResourceRepository> T getRepository(@NotNull K key,
                                                                      @Nullable String libraryName,
                                                                      @NotNull Cache<K, T> cache,
                                                                      @NotNull Supplier<T> factory) {
    T aarRepository = CacheUtils.getAndUnwrap(cache, key, factory::get);

    if (!Objects.equals(libraryName, aarRepository.getLibraryName())) {
      assert false : "Library name mismatch: " + libraryName + " vs " + aarRepository.getLibraryName();
      Logger logger = Logger.getInstance(AarResourceRepositoryCache.class);
      logger.error(new Exception("Library name mismatch: " + libraryName + " vs " + aarRepository.getLibraryName()));
    }

    return aarRepository;
  }

  public void removeProtoRepository(@NotNull Path resApkFile) {
    myProtoRepositories.invalidate(resApkFile);
  }

  public void removeSourceRepository(@NotNull ResourceFolder resourceFolder) {
    mySourceRepositories.invalidate(resourceFolder);
  }

  public void clear() {
    myProtoRepositories.invalidateAll();
    mySourceRepositories.invalidateAll();
  }

  private AarResourceRepositoryCache() {}
}
