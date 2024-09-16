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
package com.google.idea.blaze.qsync.artifacts;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.common.Label;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;

/**
 * Maps from build artifact paths (as output by the aspect via the artifact info files) to digests
 * of the build artifacts themselves.
 */
public interface DigestMap {

  /**
   * Returns the digest corresponding to a built artifact path. Returns empty if the target that
   * built the artifact failed to build (so the artifact itself was not built).
   */
  Optional<String> digestForArtifactPath(Path path, Label fromTarget);

  /** Returns all digests paths from the map that are within the given directory. */
  Iterator<Path> directoryContents(Path directory);

  /**
   * Returns a {@link BuildArtifact} corresponding to a built artifact path. Returns empty if the
   * target that built the artifact failed to build (so the artifact itself was not built).
   */
  default Optional<BuildArtifact> createBuildArtifact(Path artifactPath, Label fromTarget) {
    return digestForArtifactPath(artifactPath, fromTarget)
        .map(d -> BuildArtifact.create(d, artifactPath, fromTarget));
  }

  @VisibleForTesting
  public static DigestMap ofFunction(Function<Path, String> function) {
    return new DigestMap() {
      @Override
      public Optional<String> digestForArtifactPath(Path path, Label fromTarget) {
        return Optional.ofNullable(function.apply(path));
      }

      @Override
      public Iterator<Path> directoryContents(Path directory) {
        return Collections.emptyIterator();
      }
    };
  }
}
