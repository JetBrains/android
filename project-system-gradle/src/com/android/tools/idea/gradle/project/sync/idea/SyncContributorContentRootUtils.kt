/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.idea

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute

internal fun findCommonAncestor(file1: File, file2: File) : File {
  val path1 = file1.toPath().absolute().normalize()
  val path2 = file2.toPath().absolute().normalize()
  if (path1.root != path2.root) return File("/")

  @Suppress("PathAsIterable") // Yes, I actually want to iterate the parts of the paths
  return path1.zip(path2).fold(path1.root) {  acc, (part1, part2) ->
    if (part1 != part2) return@fold acc
    acc.resolve(part1)
  }.toFile()
}


internal fun SyncContributorAndroidProjectContext.addSourceSetToIndex(sourceSet: SourceSetData) {
  contentRootIndex.addSourceRoots(sourceSet.second.convertToExternalSourceSetModel())
}

internal fun SyncContributorAndroidProjectContext.resolveContentRoots(sourceSet: Map<out ExternalSystemSourceType?, Set<File>>): Set<Path> =
  contentRootIndex.resolveContentRoots(externalProject, sourceSet.convertToExternalSourceSetModel())

/** ContentRootIndex takes in either ExternalSourceSet (a platform model) or a DataNode for now, so need to create a temp instance here. */
private fun Map<out ExternalSystemSourceType?, Set<File>>.convertToExternalSourceSetModel() = object: ExternalSourceSet {
  override fun getSources() = this@convertToExternalSourceSetModel.entries.associate { (type, srcDirs) ->
    type to object : ExternalSourceDirectorySet {
      override fun getSrcDirs(): Set<File> = srcDirs
      override fun getName() = error("Shouldn't be used")
      override fun getOutputDir() = error("Shouldn't be used")
      override fun getGradleOutputDirs() = error("Shouldn't be used")
      override fun isCompilerOutputPathInherited() = error("Shouldn't be used")
      override fun getExcludes() = error("Shouldn't be used")
      override fun getIncludes() = error("Shouldn't be used")
      override fun getPatterns() = error("Shouldn't be used")
      override fun getFilters() = error("Shouldn't be used")
    }
  }

  override fun getName() = error("Shouldn't be used")
  override fun getJavaToolchainHome() = error("Shouldn't be used")
  override fun getSourceCompatibility() = error("Shouldn't be used")
  override fun getTargetCompatibility() = error("Shouldn't be used")
  override fun getCompilerArguments() = error("Shouldn't be used")
  override fun getArtifacts() = error("Shouldn't be used")
  override fun getDependencies() = error("Shouldn't be used")
}