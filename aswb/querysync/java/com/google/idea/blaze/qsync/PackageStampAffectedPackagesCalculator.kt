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
package com.google.idea.blaze.qsync

import java.nio.file.Path

/**
 * Calculates the set of affected packages by comparing timestamps (package stamps) of the active project data against the previously cached
 * graph data.
 *
 * @param latestProjectDataPackageStamp Current package stamps from project structure data. The key is the workspace-relative path.
 * @param latestBuildGraphDataPackageStamp Previous package stamps from query summary / build graph data. The key is the workspace-relative
 *   path.
 * @param packagesToUpdate The packages that need to be evaluated for updates (e.g. user-required packages). They should be
 *   workspace-relative paths. We assume the path is from the latest project structure.
 * @param projectScope Predicate indicating whether a package is within the project scope.
 */
fun calculatePackageStampAffectedPackages(
  latestProjectDataPackageStamp: Map<Path, Long>,
  latestBuildGraphDataPackageStamp: Map<Path, Long>,
  packagesToUpdate: Collection<Path>,
  projectScope: (Path) -> Boolean,
): AffectedPackages {
  val modifiedPackages = buildSet {
    fun addIfInProjectScope(pkg: Path) {
      if (projectScope(pkg)) add(pkg)
    }

    for (path in packagesToUpdate) {
      val newStamp = latestProjectDataPackageStamp[path] ?: continue
      val oldStamp = latestBuildGraphDataPackageStamp[path]

      if (newStamp == oldStamp) continue
      addIfInProjectScope(path)

      // When a new package is created, force update its enclosing parent package (if any)
      // since its file boundaries and target glob results have changed.
      if (oldStamp == null) {
        getNearestContainingPackage(path, latestProjectDataPackageStamp.keys)?.let { parentPkg ->
          addIfInProjectScope(parentPkg)
        }
      }
    }
  }

  // Always drop all deleted packages within the project scope so that removed packages are cleaned up from the graph.
  val deletedPackages = (latestBuildGraphDataPackageStamp.keys - latestProjectDataPackageStamp.keys).filter { projectScope(it) }.toSet()

  // TODO(b/555814875): We will handle the bzl files later
  return AffectedPackages(modifiedPackages, deletedPackages)
}

/** Finds the nearest ancestor package that contains the given [packagePath]. */
private fun getNearestContainingPackage(packagePath: Path, knownPackages: Set<Path>): Path? {
  var current: Path? = packagePath.parent
  while (current != null) {
    if (current in knownPackages) {
      return current
    }
    current = current.parent
  }

  val workspaceRoot = Path.of("")
  if (packagePath != workspaceRoot && workspaceRoot in knownPackages) {
    return workspaceRoot
  }

  return null
}
