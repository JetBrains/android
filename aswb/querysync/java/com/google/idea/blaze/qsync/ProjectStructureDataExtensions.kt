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
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.ProjectStructureRoot
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType
import com.google.idea.blaze.qsync.project.SourceSet
import java.nio.file.Path

/** Extension method to create [ProjectStructureData] from a [BuildGraphData]. */
fun ProjectStructureData.Companion.fromGraph(context: Context<*>, graph: BuildGraphData, projectIncludes: Set<Path>): ProjectStructureData {
  val javaSourceFiles = graph.getJavaSourceFiles()
  val nonJavaSourceFiles = graph.getSourceFilesByRuleKindAndType({ t -> !RuleKinds.isJava(t) }, *SourceType.all()).values.flatten()

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

  val javaSourcesMap = mutableMapOf<Path, MutableList<Path>>()
  for (file in javaSourceFiles) {
    findBuildPackage(file)?.let { pkgPath -> javaSourcesMap.computeIfAbsent(pkgPath) { mutableListOf() }.add(file) }
  }

  val nonJavaSourcesMap = mutableMapOf<Path, MutableList<Path>>()
  for (file in nonJavaSourceFiles) {
    findBuildPackage(file)?.let { pkgPath -> nonJavaSourcesMap.computeIfAbsent(pkgPath) { mutableListOf() }.add(file) }
  }

  val finalSourcesMap =
    (javaSourcesMap.keys + nonJavaSourcesMap.keys).associateWith { pkg ->
      listOf(
        SourceSet(
          rootPath = pkg,
          javaSourceFiles = javaSourcesMap[pkg]?.map { pkg.relativize(it) }?.distinct()?.sorted() ?: emptyList(),
          nonJavaSourceFiles = nonJavaSourcesMap[pkg]?.map { pkg.relativize(it) }?.sorted() ?: emptyList(),
        )
      )
    }

  val sourcesByRoot = associateByProjectRoot(finalSourcesMap, projectIncludes, context)

  val roots =
    sourcesByRoot.map { (includeRoot, packageMap) ->
      ProjectStructureRoot(projectStructureRootPath = includeRoot, packageSourceSets = packageMap)
    }

  return ProjectStructureData.create(roots = roots, activeLanguages = graph.getActiveLanguages())
}

private fun associateByProjectRoot(
  finalSourcesMap: Map<Path, List<SourceSet>>,
  projectIncludes: Set<Path>,
  context: Context<*>,
): Map<Path, Map<Path, List<SourceSet>>> {
  val sortedIncludes = projectIncludes.sortedByDescending { it.nameCount }
  val result = mutableMapOf<Path, MutableMap<Path, List<SourceSet>>>()

  for ((pkgPath, sourceSetList) in finalSourcesMap) {
    val includeRoot = sortedIncludes.find { pkgPath.startsWith(it) }
    if (includeRoot != null) {
      result.computeIfAbsent(includeRoot) { mutableMapOf() }[pkgPath] = sourceSetList
    } else {
      context.output(PrintOutput.log("WARNING: Package $pkgPath is outside all project structure roots"))
    }
  }
  return result
}
