/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.filecache;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.ForwardingCache.SimpleForwardingCache;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * A cache that handles caching files locally and maintains an in-memory cache of the same files.
 */
public class LocalDirectoryCache extends SimpleForwardingCache<String, File> {
  private static final Logger logger = Logger.getInstance(LocalDirectoryCache.class);

  // TODO(xinruiy): Reuse this class for other FileCache
  private File cacheDir;
  private CacheFetcher cacheFetcher;

  protected LocalDirectoryCache(Cache<String, File> delegate) {
    super(delegate);
  }

  public LocalDirectoryCache(File cacheDir, CacheFetcher cacheFetcher) {
    this(CacheBuilder.newBuilder().build());
    this.cacheDir = cacheDir;
    this.cacheFetcher = cacheFetcher;
  }

  /** Fetches files to cache directory and updates in-memory cache. */
  public void fetchFilesToCacheDir(ProjectViewSet projectViewSet, BlazeProjectData projectData) {
    try {
      cacheFetcher.fetch(projectViewSet, projectData, cacheDir);
    } finally {
      refresh(getCacheMap());
    }
  }

  /** Refreshes in-memory cache to match what's stored in cacheDir. */
  public void refresh() {
    FileOperationProvider.getInstance().mkdirs(cacheDir);
    if (!FileOperationProvider.getInstance().isDirectory(cacheDir)) {
      throw new IllegalArgumentException(
          "Cache Directory '" + cacheDir + "' is not a valid directory");
    }
    refresh(getCacheMap());
  }

  private void refresh(Map<String, File> cacheMap) {
    invalidateAll();
    putAll(cacheMap);
  }

  public File get(String key) throws ExecutionException {
    return get(
        key,
        () -> {
          ImmutableMap<String, File> latestValue = getCacheMap();
          if (latestValue.containsKey(key)) {
            // Refresh the whole cache map as we have retrieved all files.
            refresh(latestValue);
          } else {
            throw new IOException("Failed to find file from cache directory for key " + key);
          }
          return latestValue.get(key);
        });
  }

  /** Removes files from cache directory and invalidates the in-memory cache. */
  public void removeAll() {
    FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
    if (fileOperationProvider.exists(cacheDir)) {
      try {
        fileOperationProvider.deleteDirectoryContents(cacheDir, true);
      } catch (IOException e) {
        logger.warn("Failed to clear unpacked AAR directory: " + cacheDir, e);
      } finally {
        invalidateAll();
      }
    }
  }

  /** Visits the cache directory and generates a map from cache key to cached file. */
  private ImmutableMap<String, File> getCacheMap() {
    return stream(cacheDir.listFiles())
        .collect(toImmutableMap(cacheFetcher::getCacheKey, cacheFetcher::processCacheFile));
  }
}
