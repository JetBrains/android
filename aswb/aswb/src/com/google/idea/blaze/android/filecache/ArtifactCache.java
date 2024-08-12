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
package com.google.idea.blaze.android.filecache;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import java.nio.file.Path;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * Interface to be used by file caches. See {@link LocalArtifactCache} for more detailed JavaDoc.
 */
public interface ArtifactCache {
  /**
   * Method to initialize the cache helper. This method should be called once before any other
   * public methods
   */
  void initialize();

  /** Removes all artifacts stored in the cache. */
  void clearCache();

  /**
   * Fetches and caches the given collection of {@link OutputArtifactWithoutDigest} to disk.
   *
   * @param artifacts Collection of artifacts to add to cache
   * @param removeMissingArtifacts if true, will remove any cached artifact that is not present in
   *     {@code artifacts}.
   */
  void putAll(
      Collection<? extends OutputArtifactWithoutDigest> artifacts,
      BlazeContext context,
      boolean removeMissingArtifacts);

  /**
   * Returns the {@link Path} corresponding to the given {@link OutputArtifactWithoutDigest}, or
   * {@code null} if the artifact is not tracked in cache.
   */
  @Nullable
  Path get(OutputArtifactWithoutDigest artifact);
}
