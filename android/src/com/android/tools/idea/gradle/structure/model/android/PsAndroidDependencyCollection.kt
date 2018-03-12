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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.level2.Library
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import com.android.tools.idea.gradle.structure.model.PsParsedDependencies
import com.android.tools.idea.gradle.structure.model.pom.MavenPoms.findDependenciesInPomFile
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import java.util.function.Consumer

abstract class PsAndroidDependencyCollection(protected val parent: PsAndroidModule) : PsModelCollection<PsAndroidDependency> {

  protected val moduleDependenciesByGradlePath = mutableMapOf<String, PsModuleAndroidDependency>()
  protected val libraryDependenciesBySpec = mutableMapOf<String, PsLibraryAndroidDependency>()

  fun findElement(spec: PsArtifactDependencySpec): PsLibraryAndroidDependency? {
    val dependency = findElement(spec.toString(), PsLibraryAndroidDependency::class.java)
    return when {
      dependency != null -> dependency
      spec.version.isNullOrEmpty() ->
        libraryDependenciesBySpec.entries
          .singleOrNull { PsArtifactDependencySpec.create(it.key)?.let { it.group == spec.group && it.name == spec.name } == true }
          ?.value
      else -> null
    }
  }

  override fun <S : PsAndroidDependency> findElement(name: String, type: Class<S>): S? {
    // Note: Cannot be inlined: https://youtrack.jetbrains.com/issue/KT-23053
    @Suppress("UnnecessaryVariable")
    val uncastType = type
    return when (type) {
      PsModuleAndroidDependency::class.java -> uncastType.cast(moduleDependenciesByGradlePath[name])
      PsLibraryAndroidDependency::class.java -> uncastType.cast(libraryDependenciesBySpec[name])
      else -> null
    }
  }

  override fun forEach(consumer: Consumer<PsAndroidDependency>) {
    libraryDependenciesBySpec.values.forEach(consumer)
    moduleDependenciesByGradlePath.values.forEach(consumer)
  }

  fun forEachModuleDependency(consumer: Consumer<PsModuleAndroidDependency>) {
    moduleDependenciesByGradlePath.values.forEach(consumer)
  }

  fun findLibraryDependency(compactNotation: String): PsLibraryAndroidDependency? =
    findElement(compactNotation, PsLibraryAndroidDependency::class.java)

  fun findLibraryDependency(spec: PsArtifactDependencySpec): PsLibraryAndroidDependency? = findElement(spec)

  fun findModuleDependency(modulePath: String): PsModuleAndroidDependency? =
    findElement(modulePath, PsModuleAndroidDependency::class.java)
}

/**
 * A collection of parsed (configured) dependencies of [parent] module.
 */
class PsAndroidModuleDependencyCollection(parent: PsAndroidModule) : PsAndroidDependencyCollection(parent) {
  init {
    collectParsedDependencies()
  }

  private fun collectParsedDependencies() {
    collectParsedDependencies(parent.parsedDependencies)
  }

  private fun collectParsedDependencies(parsedDependencies: PsParsedDependencies) {
    val artifactsByConfigurationNames = buildArtifactsByConfigurations()
    val specs = mutableMapOf<PsArtifactDependencySpec, MutableList<ArtifactDependencyModel>>()
    parsedDependencies.forEachLibraryDependency { libraryDependency ->
      val spec = PsArtifactDependencySpec(
        libraryDependency.name().value(),
        libraryDependency.group().value(),
        // TODO(solodkyy): Properly handle wildcard versions.
        libraryDependency.version().value()
      )
      specs.getOrPut(spec, { mutableListOf() }).add(libraryDependency)
    }
    for ((spec, parsedModels) in specs) {
      val artifacts = parsedModels.flatMap { artifactsByConfigurationNames[it.configurationName()] ?: listOf() }.toSet()
      libraryDependenciesBySpec[spec.toString()] = PsLibraryAndroidDependency(
        parent, spec, artifacts.toList(), null, parsedModels
      )
    }
    val moduleDependencyGradlePaths = mutableMapOf<String, MutableList<ModuleDependencyModel>>()
    parsedDependencies.forEachModuleDependency { moduleDependency ->
      moduleDependencyGradlePaths.getOrPut(moduleDependency.path().value(), { mutableListOf() }).add(moduleDependency)
    }
    for ((gradlePath, parsedModels) in moduleDependencyGradlePaths) {
      val artifacts = parsedModels.flatMap { artifactsByConfigurationNames[it.configurationName()] ?: listOf() }.toSet()
      // TODO(solodkyy): Handle pre AGP 3.0 configurations.
      val resolvedModule = parent.parent.findModuleByGradlePath(gradlePath)?.resolvedModel
      moduleDependenciesByGradlePath[gradlePath] = PsModuleAndroidDependency(
        parent, gradlePath, artifacts.toList(), null,
        resolvedModule, parsedModels
      )
    }
  }

  private fun buildArtifactsByConfigurations(): Map<String, List<PsAndroidArtifact>> {
    val artifactsByConfigurationNames = mutableMapOf<String, MutableList<PsAndroidArtifact>>()
    parent.forEachVariant { variant ->
      variant.forEachArtifact { artifact ->
        artifact.possibleConfigurationNames.forEach { possibleConfigurationName ->
          artifactsByConfigurationNames.getOrPut(possibleConfigurationName, { mutableListOf() }).add(artifact)
        }
      }
    }
    return artifactsByConfigurationNames
  }
}

/**
 * A collection of resolved dependencies of a specific [artifact] of module [parent].
 */
class PsAndroidArtifactDependencyCollection(val artifact: PsAndroidArtifact) :
  PsAndroidDependencyCollection(artifact.parent.parent) {

  init {
    collectResolvedDependencies(artifact)
  }

  private fun collectResolvedDependencies(artifact: PsAndroidArtifact) {
    val resolvedArtifact = artifact.resolvedModel ?: return
    val dependencies = resolvedArtifact.level2Dependencies

    for (androidLibrary in dependencies.androidLibraries) {
      addLibrary(androidLibrary, artifact)
    }

    for (moduleLibrary in dependencies.moduleDependencies) {
      val gradlePath = moduleLibrary.projectPath
      if (gradlePath != null) {
        addModule(gradlePath, artifact, moduleLibrary.variant)
      }
    }
    for (javaLibrary in dependencies.javaLibraries) {
      addLibrary(javaLibrary, artifact)
    }
  }

  private fun addLibrary(library: Library, artifact: PsAndroidArtifact) {
    val parsedModels = mutableListOf<ArtifactDependencyModel>()
    // TODO(solodkyy): Inverse the process and match parsed dependencies with resolved instead. (See other TODOs).
    val parsedDependencies = parent.parsedDependencies

    val coordinates = GradleCoordinate.parseCoordinateString(library.artifactAddress)
    if (coordinates != null) {
      val spec = PsArtifactDependencySpec.create(coordinates)
      // TODO(b/74425541): Make sure it returns all the matching parsed dependencies rather than the first one.
      val matchingParsedDependencies =
        parsedDependencies
          .findLibraryDependencies(coordinates.groupId, coordinates.artifactId!!)
          .filter { artifact.contains(it) }
      // TODO(b/74425541): Reconsider duplicates.
      parsedModels.addAll(matchingParsedDependencies)
      val androidDependency = PsLibraryAndroidDependency(parent, spec, listOf(artifact), library, parsedModels)
      androidDependency.setDependenciesFromPomFile(findDependenciesInPomFile(library.artifact))
      libraryDependenciesBySpec[androidDependency.spec.toString()] = androidDependency
    }
  }

  private fun addModule(gradlePath: String, artifact: PsAndroidArtifact, projectVariant: String?) {
    val matchingParsedDependency =
      parent
        .parsedDependencies
        .findModuleDependency(gradlePath) { parsedDependency: DependencyModel -> artifact.contains(parsedDependency) }
    val module = parent.parent.findModuleByGradlePath(gradlePath)
    val resolvedModule = module?.resolvedModel
    val dependency =
      PsModuleAndroidDependency(
        parent,
        gradlePath,
        ImmutableList.of(artifact),
        projectVariant,
        resolvedModule,
        matchingParsedDependency.wrapInList())
    moduleDependenciesByGradlePath[gradlePath] = dependency
  }
}

@VisibleForTesting
fun versionsMatch(parsedVersion: GradleVersion, versionFromGradle: GradleVersion): Boolean {
  var result = versionFromGradle.compareTo(parsedVersion)
  if (result == 0) {
    return true
  } else if (result < 0) {
    // The "parsed" version might have a '+' sign.
    if (parsedVersion.majorSegment.acceptsGreaterValue()) {
      return true
    } else if (parsedVersion.minorSegment != null && parsedVersion.minorSegment!!.acceptsGreaterValue()) {
      return parsedVersion.major == versionFromGradle.major
    } else if (parsedVersion.microSegment != null && parsedVersion.microSegment!!.acceptsGreaterValue()) {
      result = parsedVersion.major - versionFromGradle.major
      return if (result != 0) {
        false
      } else parsedVersion.minor == versionFromGradle.minor
    }
  }
  return result == 0
}

fun <T> T?.wrapInList(): List<T> = if (this != null) listOf(this) else listOf()
