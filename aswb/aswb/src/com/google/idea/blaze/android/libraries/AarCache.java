/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.android.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nullable;

/** Local cache of the aars referenced by the project. */
public class AarCache {
  private static final Logger logger = Logger.getInstance(AarCache.class);
  /**
   * The state of the cache as of the last call to {@link #readFileState}. It will cleared by {@link
   * #clearCache}
   */
  private volatile ImmutableMap<String, File> cacheState = ImmutableMap.of();

  private static final String STAMP_FILE_NAME = "aar.timestamp";

  private final File cacheDir;

  public AarCache(File cacheDir) {
    this.cacheDir = cacheDir;
  }

  /**
   * Get the dir path to aar cache. Create one if it does exists.
   *
   * @return path to cache directory. Null will be return if the directory does not exist and fails
   *     to create one.
   */
  @Nullable
  public File getOrCreateCacheDir() throws IOException {
    FileOperationProvider fileOpProvider = FileOperationProvider.getInstance();
    // Ensure the cache dir exists
    if (!fileOpProvider.exists(cacheDir) && !fileOpProvider.mkdirs(cacheDir)) {
      throw new IOException("Fail to create cache dir " + cacheDir);
    }
    return cacheDir;
  }

  /* Get path to aar directory for the given key. The file is not guaranteed to be existed. */
  public File aarDirForKey(String key) {
    return new File(cacheDir, key);
  }

  /**
   * Create a clean aar directory for the given key. The directory will be removed and recreate if
   * the directory has already existed.
   */
  public File recreateAarDir(FileOperationProvider ops, String key) throws IOException {
    File aarDir = aarDirForKey(key);
    if (ops.exists(aarDir)) {
      ops.deleteRecursively(aarDir, true);
    }
    ops.mkdirs(aarDir);
    return aarDir;
  }

  /**
   * Create timestamp file for the given key and update its modified time. The stamp file is used to
   * identify if we need to update.
   *
   * @param key the key to retrieve aar directory from cache
   * @param aarFile the aar file that we need to create timestamp file for. The modified time of
   *     time stamp file will be updated the same as that of the aar file. So that it can be used to
   *     decide if the aar file need to be updated next time. Time stamp file will use creation time
   *     as modified time if null is provided.
   */
  public File createTimeStampFile(String key, @Nullable File aarFile) throws IOException {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    File stampFile = new File(aarDirForKey(key), STAMP_FILE_NAME);
    stampFile.createNewFile();
    if (aarFile != null) {
      long sourceTime = ops.getFileModifiedTime(aarFile);
      if (!ops.setFileModifiedTime(stampFile, sourceTime)) {
        throw new IOException("Fail to update file modified time for " + aarFile);
      }
    }
    return stampFile;
  }

  /**
   * Returns a map of cache keys for the currently-cached files, along with a representative file
   * used for timestamp-based diffing.
   *
   * <p>We use a stamp file instead of the directory itself to stash the timestamp. Directory
   * timestamps are bit more brittle and can change whenever an operation is done to a child of the
   * directory.
   *
   * <p>Also sets the in-memory @link #cacheState}.
   */
  public ImmutableMap<String, File> readFileState() {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    // Go through all of the aar directories, and get the stamp file.
    File[] unpackedAarDirectories = ops.listFiles(cacheDir);
    if (unpackedAarDirectories == null) {
      return ImmutableMap.of();
    }
    ImmutableMap<String, File> cachedFiles =
        Arrays.stream(unpackedAarDirectories)
            .collect(toImmutableMap(File::getName, dir -> new File(dir, STAMP_FILE_NAME)));
    cacheState = cachedFiles;
    return cachedFiles;
  }

  /* Remove all files that not list in retainedFiles. */
  public Collection<ListenableFuture<?>> retainOnly(ImmutableSet<String> retainedFiles) {
    ImmutableSet<String> cacheKeys = cacheState.keySet();
    ImmutableSet<String> removedKeys =
        cacheKeys.stream()
            .filter(fileName -> !retainedFiles.contains(fileName))
            .collect(toImmutableSet());

    FileOperationProvider ops = FileOperationProvider.getInstance();

    return removedKeys.stream()
        .map(
            subDir ->
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      try {
                        ops.deleteRecursively(new File(cacheDir, subDir), true);
                      } catch (IOException e) {
                        logger.warn(e);
                      }
                    }))
        .collect(toImmutableList());
  }

  /* Clean up whole cache directory and reset cache state. */
  public void clearCache() {
    FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
    if (fileOperationProvider.exists(cacheDir)) {
      try {
        fileOperationProvider.deleteDirectoryContents(cacheDir, true);
      } catch (IOException e) {
        logger.warn("Failed to clear unpacked AAR directory: " + cacheDir, e);
      }
    }
    cacheState = ImmutableMap.of();
  }

  /* Get the path to specific aar library. */
  @Nullable
  public File getCachedAarDir(String aarDirName) {
    ImmutableMap<String, File> cacheState = this.cacheState;
    if (cacheState.containsKey(aarDirName)) {
      return new File(cacheDir, aarDirName);
    }
    return null;
  }

  /* Whether the cache state is empty. */
  public boolean isEmpty() {
    return cacheState.isEmpty();
  }

  /* Get all cached aar directories. */
  public ImmutableSet<String> getCachedKeys() {
    return cacheState.keySet();
  }
}
