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
package com.google.idea.blaze.qsync.artifacts

import com.google.idea.blaze.common.Label
import java.nio.file.Path
import org.jetbrains.annotations.TestOnly

/** Maps from build artifact paths (as output by the aspect via the artifact info files) to digests of the build artifacts themselves. */
interface DigestMap {
  /**
   * Returns the digest corresponding to a built artifact path. Returns empty if the target that built the artifact failed to build (so the
   * artifact itself was not built).
   */
  fun digestForArtifactPath(path: Path, fromTarget: Label): String?

  /** Returns all digests paths from the map that are within the given directory. */
  fun directoryContents(directory: Path): Sequence<Path>

  companion object
}

/**
 * Returns a [BuildArtifact] corresponding to a built artifact path. Returns empty if the target that built the artifact failed to build (so
 * the artifact itself was not built).
 */
fun DigestMap.createBuildArtifact(artifactPath: Path, fromTarget: Label): BuildArtifact? {
  return digestForArtifactPath(artifactPath, fromTarget)?.let { BuildArtifact(it, artifactPath, fromTarget, metadata = emptyMap()) }
}

@TestOnly
fun DigestMap.Companion.ofFunction(function: (Path) -> String): DigestMap {
  return object : DigestMap {
    override fun digestForArtifactPath(path: Path, fromTarget: Label): String? {
      return function(path)
    }

    override fun directoryContents(directory: Path): Sequence<Path> {
      return emptySequence()
    }
  }
}
