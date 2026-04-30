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
fun ProjectStructureData.dump(): String {
  return buildString {
    appendLine("activeLanguages: ${activeLanguages.sorted().joinToString(", ")}")
    for (root in roots.sortedBy { it.projectStructureRootPath }) {
      if (root.buildPackages.isEmpty()) continue
      appendLine("root: ${root.projectStructureRootPath}")
      for ((pkg, buildPkg) in root.buildPackages.toSortedMap()) {
        val nonEmptySourceSets = buildPkg.sourceSets.filter { ss -> ss.javaSourceFiles.isNotEmpty() || ss.nonJavaSourceFiles.isNotEmpty() }
        if (nonEmptySourceSets.isEmpty()) continue

        appendLine("  package: $pkg")
        for (ss in nonEmptySourceSets.sortedBy { it.javaPackage }) {
          appendLine("    sourceSet:")
          appendLine("      javaPackage: '${ss.javaPackage}'")
          if (ss.javaSourceFiles.isNotEmpty()) {
            appendLine("      javaSourceFiles:")
            val seenFiles = mutableSetOf<Path>()
            for (f in ss.javaSourceFiles.sorted()) {
              if (!seenFiles.add(f)) {
                error("Duplicate file in dump: $f in package $pkg")
              }
              appendLine("        - $f")
            }
          }
          if (ss.nonJavaSourceFiles.isNotEmpty()) {
            appendLine("      nonJavaSourceFiles:")
            val seenFiles = mutableSetOf<Path>()
            for (f in ss.nonJavaSourceFiles.sorted()) {
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
}
