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
package com.google.idea.blaze.qsync

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.qsync.query.PackageSet
import java.nio.file.Path

/**
 * Determines Java package prefixes for directories that contain source files.
 */
interface JavaPackagePrefixReader {
  /**
   * Concurrently reads the package name for each package in the provided [PackageSet] to
   * determine the package prefix for each source directory.
   *
   * For each package, this method scans its subdirectories to find source files. It then reads the
   * package declaration from a representative file in each directory that contains sources. This
   * correctly discovers all distinct subpackages that should be treated as source roots.
   *
   * @return A map where keys are the workspace-relative paths of directories containing source
   *   files (i.e., subpackages), and values are the corresponding Java/Kotlin package names, which
   *   serve as the prefix.
   */
  suspend fun readPrefixes(context: Context<*>, packages: PackageSet, sourceFiles: Collection<Path>): Map<Path, String>
}
