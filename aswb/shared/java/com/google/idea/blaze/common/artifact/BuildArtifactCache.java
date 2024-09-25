/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common.artifact;

import com.google.common.collect.ImmutableCollection;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.exception.BuildException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * A cache of build artifacts.
 *
 * <p>Downloads build artifacts on request, identifying them based on their digest as provided by
 * {@link OutputArtifact#getDigest()}.
 *
 * <p>For artifacts that have previously been requested via {@link #addAll(ImmutableCollection,
 * Context)}, provides access to their contents as a local file via {@link #get(String)}.
 *
 * <p>Access times are updated when artifacts downloads are requested, and when the contents are
 * requested, to enable unused cache entries to be cleaned up later on (not implemented yet).
 */
public interface BuildArtifactCache {

  /**
   * Interface used to request cleaning when the IDE is idle.
   *
   * <p>The cache itself will make a request to be cleaned, and IDE integration code implements it.
   */
  interface CleanRequest {

    /**
     * Request that {@link BuildArtifactCache#clean(long, Duration)} is called when the IDE is idle
     * and after a delay. Any existing unfilled request if replaced by this more recent one.
     */
    void request();

    /**
     * Cancels any previous request to {@link #request()}.
     *
     * <p>This will be called if there is new cache activity (e.g. adding more items) while a clean
     * request is active, to help ensure that a clean operation does not block a user operation such
     * as a dependencies build.
     *
     * <p>The implementer should ensure that no call to {@link #clean(long, Duration)} is made after
     * this method is called, until the next call to {@link #request()}.
     */
    void cancel();
  }

  static BuildArtifactCache create(
      Path cacheDir,
      ArtifactFetcher<OutputArtifact> fetcher,
      ListeningExecutorService executor,
      CleanRequest cleanRequest)
      throws BuildException {
    return new BuildArtifactCacheDirectory(cacheDir, fetcher, executor, cleanRequest);
  }

  /**
   * Requests that the given artifacts are added to the cache.
   *
   * @return A future that will complete once all artifacts have been added to the cache. The future
   *     will fail if we fail to add any artifact to the cache.
   */
  ListenableFuture<?> addAll(ImmutableCollection<OutputArtifact> artifacts, Context<?> context);

  /**
   * Returns a bytesource of an artifact that was previously added to the cache.
   *
   * @return A future of the byte source if the artifact is already present, or is in the process of
   *     being requested. Empty if the artifact has never been added to the cache, or has been
   *     deleted since then.
   */
  Optional<ListenableFuture<CachedArtifact>> get(String digest);

  /**
   * Synchronously clean the cache.
   *
   * @param maxTargetSizeBytes The maximum target size of the cache. Nothing will be deleted if the
   *     total cache size is smaller than this.
   * @param minKeepDuration The minimum duration that artifacts should be kept in the cache. This
   *     constraint may result in {@code maxTargetSizeBytes} being exceeded. The age of an artifact
   *     is determined by the time passed since it was added or last accessed via {@link
   *     #get(String)}.
   */
  void clean(long maxTargetSizeBytes, Duration minKeepDuration) throws BuildException;

  /** Remove all items from the cache. */
  void purge() throws BuildException;
}
