/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.google.common.collect.ArrayListMultimap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.containers.ContainerUtil.getFirstItem
import org.gradle.tooling.model.GradleModuleVersion

class PsParsedDependencies(parsedModel: GradleBuildModel?) {
  // Key: module's Gradle path
  private val parsedModuleDependencies = ArrayListMultimap.create<String, ModuleDependencyModel>()

  // Key: artifact group ID + ":" + artifact name (e.g. "com.google.guava:guava")
  private val parsedArtifactDependencies = ArrayListMultimap.create<String, ArtifactDependencyModel>()

  init {
    reset(parsedModel)
  }

  fun reset(parsedModel: GradleBuildModel?) {
    parsedArtifactDependencies.clear()
    parsedModuleDependencies.clear()
    if (parsedModel != null) {
      ApplicationManager.getApplication().runReadAction {
        for (parsedDependency in parsedModel.dependencies().all()) {
          when (parsedDependency) {
            is ArtifactDependencyModel -> parsedArtifactDependencies.put(createIdFrom(parsedDependency), parsedDependency)
            is ModuleDependencyModel -> parsedModuleDependencies.put(parsedDependency.path().value(), parsedDependency)
          }
        }
      }
    }
  }

  fun findLibraryDependencies(
    spec: PsArtifactDependencySpec,
    predicate: ((ArtifactDependencyModel) -> Boolean)?
  ): List<ArtifactDependencyModel> {
    val id = createIdFrom(spec)
    val potentialMatches = parsedArtifactDependencies.get(id)
    return if (predicate != null) {
      potentialMatches.filter(predicate)
    } else potentialMatches.toList()
  }

  fun findLibraryDependency(
    coordinates: GradleCoordinate,
    predicate: (ArtifactDependencyModel) -> Boolean
  ): ArtifactDependencyModel? =
    parsedArtifactDependencies.get(createIdFrom(coordinates)).find(predicate)

  fun findLibraryDependency(moduleVersion: GradleModuleVersion): ArtifactDependencyModel? {
    val potentialMatches = parsedArtifactDependencies.get(createIdFrom(moduleVersion))
    if (potentialMatches.size == 1) {
      // Only one found. Just use it.
      return getFirstItem(potentialMatches)
    }

    val version = moduleVersion.version.orEmpty()

    val dependenciesByVersion = mutableMapOf<GradleVersion, ArtifactDependencyModel>()
    for (potentialMatch in potentialMatches) {
      val potentialVersion = potentialMatch.version().value().orEmpty()
      if (version == potentialVersion) {
        // Perfect version match. Use it.
        return potentialMatch
      }
      if (potentialVersion.isNotEmpty()) {
        // Collect all the "parsed" dependencies with same group and name, to make a best guess later.
        val parsedVersion = GradleVersion.tryParse(potentialVersion)
        if (parsedVersion != null) {
          dependenciesByVersion[parsedVersion] = potentialMatch
        }
      }
    }

    if (version.isNotEmpty() && !dependenciesByVersion.isEmpty()) {
      val parsedVersion = GradleVersion.tryParse(version)
      if (parsedVersion != null) {
        for (potentialVersion in dependenciesByVersion.keys) {
          // TODO(solodkyy): Can there be more than one item satisfying the condition?
          if (parsedVersion > potentialVersion) {
            return dependenciesByVersion[potentialVersion]
          }
        }
      }
    }

    return null
  }

  fun findModuleDependency(gradlePath: String, predicate: (ModuleDependencyModel) -> Boolean): ModuleDependencyModel? =
    parsedModuleDependencies.get(gradlePath).find(predicate)

  private fun createIdFrom(dependency: ArtifactDependencyModel) =
    joinAsGradlePath(listOf(dependency.group().value(), dependency.name().value()))

  private fun createIdFrom(spec: PsArtifactDependencySpec) =
    joinAsGradlePath(listOf(spec.group, spec.name))

  private fun createIdFrom(coordinates: GradleCoordinate) =
    joinAsGradlePath(listOf(coordinates.groupId, coordinates.artifactId))

  private fun createIdFrom(moduleVersion: GradleModuleVersion) =
    joinAsGradlePath(listOf(moduleVersion.group, moduleVersion.name))

  private fun joinAsGradlePath(segments: List<String?>) =
    segments.filterNotNull().joinToString(GRADLE_PATH_SEPARATOR)
}
