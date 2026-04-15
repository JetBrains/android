/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project.testutil

import com.google.idea.blaze.qsync.project.ProjectStructureData
import java.nio.file.Path

/** Utility to dump [ProjectStructureData] to a stable string representation for testing. */
fun ProjectStructureData.dump(ignoredFiles: Set<Path> = emptySet()): String {
  return buildString {
    appendLine("activeLanguages: ${activeLanguages.sorted().joinToString(", ")}")
    for (root in roots.sortedBy { it.projectStructureRootPath }) {
      if (root.packageSourceSets.isEmpty()) continue
      appendLine("root: ${root.projectStructureRootPath}")
      for ((pkg, ss) in root.packageSourceSets.toSortedMap()) {
        val filteredJavaSourceFiles = ss.javaSourceFiles.filter { it !in ignoredFiles }
        val filteredNonJavaSourceFiles = ss.nonJavaSourceFiles.filter { it !in ignoredFiles }

        if (filteredJavaSourceFiles.isEmpty() && filteredNonJavaSourceFiles.isEmpty()) continue

        appendLine("  package: $pkg")
        appendLine("    sourceSet:")
        if (filteredJavaSourceFiles.isNotEmpty()) {
          appendLine("      javaSourceFiles:")
          val seenFiles = mutableSetOf<Path>()
          for (f in filteredJavaSourceFiles.sorted()) {
            if (!seenFiles.add(f)) {
              error("Duplicate file in dump: $f in package $pkg")
            }
            appendLine("        - $f")
          }
        }
        if (filteredNonJavaSourceFiles.isNotEmpty()) {
          appendLine("      nonJavaSourceFiles:")
          val seenFiles = mutableSetOf<Path>()
          for (f in filteredNonJavaSourceFiles.sorted()) {
            if (!seenFiles.add(f)) {
              error("Duplicate file in dump: $f in package $pkg")
            }
            appendLine("        - $f")
          }
        }
      }
    }
  }
}
