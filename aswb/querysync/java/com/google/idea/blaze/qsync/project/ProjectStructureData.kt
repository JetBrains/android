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

import com.google.idea.blaze.qsync.query.PackageSet
import java.nio.file.Path

/**
 * A data class to hold the information required to setup a basic project structure.
 *
 * This class encapsulates a subset of data from [BuildGraphData] that is needed by
 * [GraphToProjectConverter] to setup a basic project. Its contents can be instantiated from a
 * directory traversal and without running `bazel query`.
 */
data class ProjectStructureData(
  val javaSourceFiles: List<Path>,
  val packages: PackageSet,
  val nonJavaSourceFiles: List<Path>,
  val activeLanguages: Set<QuerySyncLanguage>,
)
