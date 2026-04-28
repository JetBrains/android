/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common.artifact

import java.nio.file.Path

/** A descriptor of an output artifact that contains data needed to identify the artifact. */
interface OutputArtifact {
  /**
   * Artifact path as Bazel's File.path returns it, i.e. workspace relative.
   *
   * Examples (implementation details, **do not rely on them**) (1) an artifact produced by a Bazel rule (a generated artifact) normally
   * gets a relative path starting with `bazel-out` followed by a configuration mnemonic like `k8-opt` and a directory like `bin`,
   * `gen-files` etc; (2) an artifact coming from the source code tree under the project workspace gets a relative path not stating with any
   * known system prefix like `bazel-out`; (3) in non hermetic builds some artifacts may come from the local file system outside the project
   * workspace and their path is usually and absolute path.
   *
   * Note that even though an artifact path is a workspace relative path it does not mean that `workspace.resolve(artifactPath)` returns a
   * path pointing to a file representing the artifact (bazel-out may actually be located elsewhere or the artifact can be a remote one). An
   * artifact path is an artifact id. The following two usages are anticipated: (1) matching artifacts with artifact paths coming from
   * various info files produced by bazel aspects and (2) to select the location under a given directory where to place the artifact
   * (caution needs to be exercised when a path is absolute or starts with `..`).
   *
   * Components of this path should be inspected individually (except the file name), i.e. do not test for the presence of `bazel-out` etc.
   */
  val artifactPath: Path

  /** The length of a prefix path if any, i.e. the length of a prefix like `basel-out/k8/bin` is 3. */
  val artifactPathPrefixLength: Int

  /**
   * The digest of the artifact file.
   *
   * The digest of the artifact file; using the build tool's configured digest algorithm. It represents the content of the file and can be
   * used to detect whether the content has changed.
   */
  val digest: String

  /** Returns the length of the underlying file in bytes, or 0 if this can't be determined. */
  val length: Long

  @get:Deprecated("")
  val bazelOutRelativePath: String
    /**
     * The blaze-out-relative path.
     *
     * **Do not use** as it is quirky due to compatibility with old code. See: [.artifactPathToRelativePath]
     */
    get() {
      val artifactPath = this.artifactPath
      if (artifactPath!!.startsWith("bazel-out") || artifactPath.startsWith("blaze-out")) {
        // bazel-out or blaze-out is one prefix, so pass 1.
        return Companion.artifactPathToRelativePath(artifactPath, 1)
      }
      return artifactPath.toString()
    }

  companion object {
    /**
     * Returns the relative part of an artifact path with respect to the prefix length in the way compatible with the old implementation
     * that leave workspace paths intact without replacing them with ../artifact_path.
     */
    @JvmStatic
    fun artifactPathToRelativePath(artifactPath: Path, artifactPathPrefixLength: Int): String {
      return artifactPath.subpath(artifactPathPrefixLength, artifactPath.getNameCount()).toString()
    }
  }
}
