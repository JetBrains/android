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
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import com.android.tools.idea.gradle.structure.model.PsParsedDependencies
import com.android.tools.idea.gradle.structure.model.pom.MavenPoms.findDependenciesInPomFile
import com.google.common.collect.ImmutableList
import com.google.common.collect.LinkedListMultimap
import java.util.function.Consumer

abstract class PsAndroidDependencyCollection(protected val parent: PsAndroidModule) : PsModelCollection<PsAndroidDependency> {
  protected data class LibraryKey(val group: String, val name: String)

  protected val moduleDependenciesByGradlePath = LinkedListMultimap.create<String, PsModuleAndroidDependency>()!!
  protected val libraryDependenciesBySpec = LinkedListMultimap.create<LibraryKey, PsLibraryAndroidDependency>()!!

  override fun forEach(consumer: Consumer<PsAndroidDependency>) {
    libraryDependenciesBySpec.values().forEach(consumer)
    moduleDependenciesByGradlePath.values().forEach(consumer)
  }

  fun forEachModuleDependency(consumer: Consumer<PsModuleAndroidDependency>) {
    moduleDependenciesByGradlePath.values().forEach(consumer)
  }

  fun findLibraryDependencies(group: String?, name: String): List<PsLibraryAndroidDependency> =
    libraryDependenciesBySpec[LibraryKey(group.orEmpty(), name)].toList()

  fun isEmpty(): Boolean = moduleDependenciesByGradlePath.isEmpty && libraryDependenciesBySpec.isEmpty

  protected fun PsArtifactDependencySpec.toLibraryKey(): LibraryKey = LibraryKey(group.orEmpty(), name)
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

  fun reindex() {
    val libraryDependencies = libraryDependenciesBySpec.values().toList()
    val moduleDependencies = moduleDependenciesByGradlePath.values().toList()

    libraryDependenciesBySpec.clear()
    libraryDependencies.forEach { libraryDependenciesBySpec.put(it.spec.toLibraryKey(), it) }

    moduleDependenciesByGradlePath.clear()
    moduleDependencies.forEach { moduleDependenciesByGradlePath.put(it.gradlePath, it) }
  }

  private fun collectParsedDependencies(parsedDependencies: PsParsedDependencies) {
    val artifactsByConfigurationNames = buildArtifactsByConfigurations()
    parsedDependencies.forEachLibraryDependency { libraryDependency ->
      val artifacts = artifactsByConfigurationNames[libraryDependency.configurationName()] ?: listOf()
      val declaredDependency = PsDeclaredLibraryAndroidDependency(parent, artifacts, libraryDependency)
      libraryDependenciesBySpec.put(declaredDependency.spec.toLibraryKey(), declaredDependency)
    }
    parsedDependencies.forEachModuleDependency { moduleDependency ->
      val gradlePath = moduleDependency.path().value()
      val artifacts = artifactsByConfigurationNames[moduleDependency.configurationName()] ?: listOf()
      moduleDependenciesByGradlePath.put(
        gradlePath, PsDeclaredModuleAndroidDependency(
        parent, gradlePath, artifacts.toList(), moduleDependency.configurationName(), null,
        moduleDependency)
      )
    }
  }

  private fun buildArtifactsByConfigurations(): Map<String, List<PsAndroidArtifact>> {
    val artifactsByConfigurationNames = mutableMapOf<String, MutableList<PsAndroidArtifact>>()
    parent.variants.forEach { variant ->
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
      val androidDependency = PsResolvedLibraryAndroidDependency(parent, spec, listOf(artifact), library, parsedModels)
      androidDependency.setDependenciesFromPomFile(findDependenciesInPomFile(library.artifact))
      libraryDependenciesBySpec.put(androidDependency.spec.toLibraryKey(), androidDependency)
    }
  }

  private fun addModule(gradlePath: String, artifact: PsAndroidArtifact, projectVariant: String?) {
    val matchingParsedDependency =
      parent
        .parsedDependencies
        .findModuleDependency(gradlePath) { parsedDependency: DependencyModel -> artifact.contains(parsedDependency) }
    val module = parent.parent.findModuleByGradlePath(gradlePath)
    val resolvedModule = module?.resolvedModel
    if (resolvedModule != null) {
      val dependency =
        PsResolvedModuleAndroidDependency(
          parent,
          gradlePath,
          ImmutableList.of(artifact),
          matchingParsedDependency?.configurationName() ?: "",
          projectVariant,
          resolvedModule,
          matchingParsedDependency.wrapInList())
      moduleDependenciesByGradlePath.put(gradlePath, dependency)
    }
    // else we have a resolved dependency on a removed module (or composite build etc.).
  }
}

private fun <T> T?.wrapInList(): List<T> = if (this != null) listOf(this) else listOf()
