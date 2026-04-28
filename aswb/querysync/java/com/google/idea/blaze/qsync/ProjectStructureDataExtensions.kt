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

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.RuleKinds
import com.google.idea.blaze.qsync.java.PackageReader
import com.google.idea.blaze.qsync.java.choosePackageCandidate
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.FileExtensions
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.ProjectStructureRoot
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType
import com.google.idea.blaze.qsync.project.SourceSet
import java.nio.file.Files
import java.nio.file.Path

/** Extension method to create [ProjectStructureData] from a [BuildGraphData]. */
fun ProjectStructureData.Companion.fromGraph(
  context: Context<*>,
  graph: BuildGraphData,
  projectIncludes: Set<Path>,
  workspaceRoot: Path,
  packageReader: PackageReader,
  parallelPackageReader: PackageReader.ParallelReader,
  fileExtensions: FileExtensions = FileExtensions(),
  fileExists: (Path) -> Boolean = { path -> Files.isRegularFile(workspaceRoot.resolve(path)) },
): ProjectStructureData {
  val javaSourceFiles = graph.getJavaSourceFiles()
  val nonJavaSourceFiles =
    graph.getSourceFilesByRuleKindAndType({ t: String -> !RuleKinds.isJava(t) }, *SourceType.all()).values.flatten().distinct()

  val packages = graph.packages()
  val directoryToContainingPackageMap = mutableMapOf<Path, Path?>()

  fun findBuildPackage(filePath: Path): Path? {
    val parent = filePath.parent ?: return null
    return directoryToContainingPackageMap.getOrPut(parent) {
      var current: Path? = parent
      while (current != null) {
        if (packages.contains(current)) {
          return@getOrPut current
        }
        current = current.parent
      }
      null
    }
  }

  val filesByDir = javaSourceFiles.groupBy { it.parent ?: Path.of("") }
  val candidateFiles = filesByDir.values.mapNotNull { files -> choosePackageCandidate(files, fileExtensions, fileExists) }

  val candidateFileToPackageMap = parallelPackageReader.readPackages(context, packageReader, candidateFiles)

  val fileToPackageMap = mutableMapOf<Path, String>()
  for (files in filesByDir.values) {
    val candidate = choosePackageCandidate(files, fileExtensions, fileExists)
    val javaPackage = candidate?.let { candidateFileToPackageMap[it] } ?: ""
    for (file in files) {
      fileToPackageMap[file] = javaPackage
    }
  }

  val javaSourcesMap = mutableMapOf<PackageKey, MutableList<Path>>()
  for (file in javaSourceFiles) {
    val buildPackage = findBuildPackage(file) ?: continue
    val javaPackage = fileToPackageMap[file] ?: ""
    val key = PackageKey(buildPackage, javaPackage)
    javaSourcesMap.computeIfAbsent(key) { mutableListOf() }.add(file)
  }

  val nonJavaSourcesMap = mutableMapOf<Path, MutableList<Path>>()
  for (file in nonJavaSourceFiles) {
    findBuildPackage(file)?.let { pkgPath -> nonJavaSourcesMap.computeIfAbsent(pkgPath) { mutableListOf() }.add(file) }
  }

  val allPackageKeys = javaSourcesMap.keys + nonJavaSourcesMap.keys.map { PackageKey(it, "") }
  val finalSourcesMap =
    allPackageKeys.associateWith { key ->
      val pkg = key.buildPackage
      SourceSet(
        rootPath = pkg,
        javaSourceFiles = javaSourcesMap[key]?.map { pkg.relativize(it) }?.distinct()?.sorted() ?: emptyList(),
        nonJavaSourceFiles =
          if (key.javaPackage.isEmpty()) nonJavaSourcesMap[pkg]?.map { pkg.relativize(it) }?.sorted() ?: emptyList() else emptyList(),
        javaPackage = key.javaPackage,
      )
    }

  val sourcesByRoot = associateByProjectRoot(finalSourcesMap, projectIncludes, context)

  val roots =
    sourcesByRoot.map { (includeRoot, packageMap) ->
      ProjectStructureRoot(projectStructureRootPath = includeRoot, packageSourceSets = packageMap.mapValues { it.value.toList() })
    }

  return ProjectStructureData.create(roots = roots, activeLanguages = graph.getActiveLanguages())
}

private data class PackageKey(val buildPackage: Path, val javaPackage: String)

private fun associateByProjectRoot(
  finalSourcesMap: Map<PackageKey, SourceSet>,
  projectIncludes: Set<Path>,
  context: Context<*>,
): Map<Path, Map<Path, List<SourceSet>>> {
  val sortedIncludes = projectIncludes.sortedByDescending { it.nameCount }
  val result = mutableMapOf<Path, MutableMap<Path, MutableList<SourceSet>>>()

  for ((key, sourceSet) in finalSourcesMap) {
    val includeRoot = sortedIncludes.find { key.buildPackage.startsWith(it) }
    if (includeRoot != null) {
      val rootMap = result.computeIfAbsent(includeRoot) { mutableMapOf() }
      rootMap.computeIfAbsent(key.buildPackage) { mutableListOf() }.add(sourceSet)
    } else {
      context.output(PrintOutput.log("WARNING: Package ${key.buildPackage} is outside all project structure roots"))
    }
  }
  return result
}
