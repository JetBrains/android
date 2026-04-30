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

import com.google.idea.blaze.common.Label
import java.nio.file.Path

/** Data class to hold the source files within a single build package. */
data class SourceSet(
  /** The path to the directory containing the sources, relative to the workspace root. */
  val rootPath: Path,
  /** Java/Kotlin source files, relative to the rootPath. */
  val javaSourceFiles: List<Path> = emptyList(),
  /** Other source files (e.g. C++, Proto), relative to the rootPath. */
  val nonJavaSourceFiles: List<Path> = emptyList(),
  /** The Java package of the sources, if applicable. */
  val javaPackage: String,
) {
  init {
    fun validatePath(name: String, path: Path) {
      require(!path.isAbsolute) { "$name must be relative: $path" }
      require(path.normalize() == path) { "$name must be normalized: $path" }
      require(!path.toString().startsWith("..")) { "$name must not start with ..: $path" }
    }

    validatePath("rootPath", rootPath)
    javaSourceFiles.forEach { validatePath("javaSourceFile", it) }
    nonJavaSourceFiles.forEach { validatePath("nonJavaSourceFile", it) }
  }
}

/** Data class to hold the source sets within a single build package. */
data class BuildPackage(val path: Path, val sourceSets: List<SourceSet>)

/** Data class to hold the build packages associated with a project structure root. */
data class ProjectStructureRoot(val projectStructureRootPath: Path, val buildPackages: Map<Path, BuildPackage>) {
  init {
    buildPackages.values
      .flatMap { it.sourceSets }
      .forEach { sourceSet ->
        require(sourceSet.rootPath.startsWith(projectStructureRootPath)) {
          "SourceSet rootPath (${sourceSet.rootPath}) must start with projectStructureRootPath ($projectStructureRootPath)"
        }
      }
  }
}

/**
 * A data class to hold the information required to setup a basic project structure.
 *
 * This class encapsulates a subset of data from [BuildGraphData] that is needed by [GraphToProjectConverter] to setup a basic project. Its
 * contents can be instantiated from a directory traversal and without running `bazel query`.
 */
data class ProjectStructureData private constructor(val roots: List<ProjectStructureRoot>, val activeLanguages: Set<QuerySyncLanguage>) {
  companion object {
    fun create(roots: List<ProjectStructureRoot>, activeLanguages: Set<QuerySyncLanguage>): ProjectStructureData {
      return ProjectStructureData(roots, activeLanguages.toHashSet())
    }

    @JvmField val EMPTY = create(roots = emptyList(), activeLanguages = emptySet())
  }
}

/** Finds the [ProjectStructureRoot] containing the given path. */
fun ProjectStructureData.getProjectStructureRoot(path: Path): ProjectStructureRoot? {
  return roots.filter { path.startsWith(it.projectStructureRootPath) }.maxByOrNull { it.projectStructureRootPath.nameCount }
}

/** Finds the [BuildPackage] containing the given path. */
fun ProjectStructureData.getBuildPackage(path: Path): BuildPackage? {
  val root = getProjectStructureRoot(path) ?: return null
  var current = path
  while (current != null) {
    val buildPkg = root.buildPackages[current]
    if (buildPkg != null) {
      return buildPkg
    }
    if (current == root.projectStructureRootPath) break
    current = current.parent
  }
  return null
}

/** Returns a [Label] representing the given path in the workspace with the current build packages. The file does not need to exist. */
fun ProjectStructureData.pathToLabel(file: Path): Label? {
  val buildPkg = getBuildPackage(file) ?: return null
  return Label.of("//${buildPkg.path}:" + buildPkg.path.relativize(file).toString())
}
