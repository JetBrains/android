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
import com.google.idea.blaze.qsync.java.choosePackageCandidates
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.BuildPackage
import com.google.idea.blaze.qsync.project.FileExtensions
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.ProjectStructureRoot
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType
import com.google.idea.blaze.qsync.project.SourceSet
import com.google.idea.blaze.qsync.project.getBuildPackage
import com.google.idea.blaze.qsync.project.getJavaSourceFiles
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

  val directoryToContainingPackageMap = mutableMapOf<Path, Path?>()

  fun findBuildPackage(filePath: Path): Path? {
    val parent = filePath.parent ?: return null
    return directoryToContainingPackageMap.getOrPut(parent) {
      var current: Path? = parent
      while (current != null) {
        if (graph.getBuildPackage(current) != null) {
          return@getOrPut current
        }
        current = current.parent
      }
      null
    }
  }

  val filesByDir = javaSourceFiles.groupBy { it.parent ?: Path.of("") }
  val candidateFiles = filesByDir.values.map { files -> choosePackageCandidates(files, fileExtensions, exists = fileExists) }

  val candidateFileToPackageMap = parallelPackageReader.readPackages(context, packageReader, candidateFiles)
  val dirToPackageMap = candidateFileToPackageMap.mapKeys { it.key.parent ?: Path.of("") }

  val fileToPackageMap = mutableMapOf<Path, String>()
  for ((dir, files) in filesByDir) {
    val javaPackage = dirToPackageMap[dir] ?: ""
    for (file in files) {
      fileToPackageMap[file] = javaPackage
    }
  }

  val javaSourcesMap = mutableMapOf<Path, MutableMap<String, MutableList<Path>>>()
  for (file in javaSourceFiles) {
    val buildPackage = findBuildPackage(file) ?: continue
    val javaPackage = fileToPackageMap[file] ?: ""
    javaSourcesMap.computeIfAbsent(buildPackage) { mutableMapOf() }.computeIfAbsent(javaPackage) { mutableListOf() }.add(file)
  }

  val nonJavaSourcesMap = mutableMapOf<Path, MutableList<Path>>()
  for (file in nonJavaSourceFiles) {
    findBuildPackage(file)?.let { pkgPath -> nonJavaSourcesMap.computeIfAbsent(pkgPath) { mutableListOf() }.add(file) }
  }

  val allBuildPackages = (javaSourcesMap.keys + nonJavaSourcesMap.keys).distinct()
  val finalBuildPackages =
    allBuildPackages.associateWith { buildPackage ->
      val javaPackages = javaSourcesMap[buildPackage]?.keys ?: emptySet()
      val allPackages = if (nonJavaSourcesMap.containsKey(buildPackage)) javaPackages + "" else javaPackages

      BuildPackage(
        path = buildPackage,
        sourceSets =
          allPackages.map { javaPackage ->
            val javaSources = javaSourcesMap[buildPackage]?.get(javaPackage) ?: emptyList()
            val nonJavaSources = if (javaPackage.isEmpty()) nonJavaSourcesMap[buildPackage] ?: emptyList() else emptyList()
            SourceSet(
              rootPath = buildPackage,
              javaSourceFiles = javaSources.map { buildPackage.relativize(it) }.distinct().sorted(),
              nonJavaSourceFiles = nonJavaSources.map { buildPackage.relativize(it) }.distinct().sorted(),
              javaPackage = javaPackage,
            )
          },
      )
    }

  val sourcesByRoot = associateByProjectRoot(finalBuildPackages, projectIncludes, context)

  val roots =
    sourcesByRoot.map { (includeRoot, buildPackages) ->
      ProjectStructureRoot(projectStructureRootPath = includeRoot, buildPackages = buildPackages)
    }

  return ProjectStructureData.create(roots = roots, activeLanguages = graph.getActiveLanguages())
}

private fun associateByProjectRoot(
  finalBuildPackages: Map<Path, BuildPackage>,
  projectIncludes: Set<Path>,
  context: Context<*>,
): Map<Path, Map<Path, BuildPackage>> {
  val sortedIncludes = projectIncludes.sortedByDescending { it.nameCount }
  val result = mutableMapOf<Path, MutableMap<Path, BuildPackage>>()

  for ((pkgPath, buildPkg) in finalBuildPackages) {
    val includeRoot = sortedIncludes.find { pkgPath.startsWith(it) }
    if (includeRoot != null) {
      val rootMap = result.computeIfAbsent(includeRoot) { mutableMapOf() }
      rootMap[pkgPath] = buildPkg
    } else {
      context.output(PrintOutput.log("WARNING: Package $pkgPath is outside all project structure roots"))
    }
  }
  return result
}
