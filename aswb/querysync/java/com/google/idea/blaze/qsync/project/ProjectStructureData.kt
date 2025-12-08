/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project

import java.nio.file.Path

/** Data class to hold the source files within a single build package. */
data class SourceSet(
  /** Java/Kotlin source files, relative to the workspace root. */
  val javaSourceFiles: List<Path> = emptyList(),
  /** Other source files (e.g. C++, Proto), relative to the workspace root. */
  val nonJavaSourceFiles: List<Path> = emptyList(),
)

/**
 * A data class to hold the information required to setup a basic project structure.
 *
 * This class encapsulates a subset of data from [BuildGraphData] that is needed by
 * [GraphToProjectConverter] to setup a basic project. Its
 * contents can be instantiated from a directory traversal and without running `bazel query`.
 */
data class ProjectStructureData(
  /** Map from build package path (relative to workspace root) to its source files. */
  val packageSourceSets: Map<Path, SourceSet>,
  val activeLanguages: Set<QuerySyncLanguage>,
) {
  companion object {
    @JvmField
    val EMPTY =
      ProjectStructureData(
        packageSourceSets = emptyMap(),
        activeLanguages = emptySet(),
      )
  }
}
