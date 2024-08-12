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
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** Mock of {@link ArtifactCache}. Does not require actual files to present in the file system */
public class MockArtifactCache implements ArtifactCache {
  // Maps an artifact's relative path to it's path in cache
  private final Map<String, String> relativePathToFile;

  public MockArtifactCache() {
    this.relativePathToFile = new HashMap<>();
  }

  @Override
  public void initialize() {}

  @Override
  public void clearCache() {
    relativePathToFile.clear();
  }

  @Override
  public void putAll(
      Collection<? extends OutputArtifactWithoutDigest> artifacts,
      BlazeContext context,
      boolean removeMissingArtifacts) {
    throw new UnsupportedOperationException(
        "MockArtifactCache does not support putting files in file system. Use `addTrackedFile`"
            + " instead");
  }

  @Nullable
  @Override
  public Path get(OutputArtifactWithoutDigest artifact) {
    CacheEntry cacheEntry;
    try {
      cacheEntry = CacheEntry.forArtifact(artifact);
    } catch (ArtifactNotFoundException e) {
      return null;
    }
    return Paths.get(
        relativePathToFile.get(
            cacheEntry.getArtifacts().stream().findFirst().get().getRelativePath()));
  }

  public void addTrackedFile(OutputArtifactWithoutDigest artifact, String trackedFilePath) {
    CacheEntry cacheEntry = null;
    try {
      cacheEntry = CacheEntry.forArtifact(artifact);
    } catch (ArtifactNotFoundException e) {
      return;
    }
    relativePathToFile.put(
        cacheEntry.getArtifacts().stream().findFirst().get().getRelativePath(), trackedFilePath);
  }
}
