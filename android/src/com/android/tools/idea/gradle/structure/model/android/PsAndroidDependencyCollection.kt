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
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.structure.model.*
import com.google.common.collect.ImmutableList

/**
 * A collection of parsed (configured) dependencies of [parent] module.
 */
class PsAndroidModuleDependencyCollection(parent: PsAndroidModule)
  : PsDeclaredDependencyCollection<PsAndroidModule, PsDeclaredLibraryAndroidDependency, PsDeclaredModuleAndroidDependency>(
  parent
) {
  private var artifactsByConfigurationNames: Map<String, List<PsAndroidArtifact>> = mapOf()
  override fun initParsedDependencyCollection() {
    artifactsByConfigurationNames = buildArtifactsByConfigurations()
  }

  override fun createLibraryDependency(artifactDependencyModel: ArtifactDependencyModel): PsDeclaredLibraryAndroidDependency {
    val artifacts = artifactsByConfigurationNames[artifactDependencyModel.configurationName()] ?: listOf()
    return PsDeclaredLibraryAndroidDependency(parent, artifacts, artifactDependencyModel)
  }

  override fun createModuleDependency(moduleDependencyModel: ModuleDependencyModel): PsDeclaredModuleAndroidDependency {
    val gradlePath = moduleDependencyModel.path().forceString()
    val artifacts = artifactsByConfigurationNames[moduleDependencyModel.configurationName()] ?: listOf()
    return PsDeclaredModuleAndroidDependency(
      parent, gradlePath, artifacts.toList(), moduleDependencyModel.configurationName(), null,
      moduleDependencyModel)
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
class PsAndroidArtifactDependencyCollection(val artifact: PsAndroidArtifact)
  : PsResolvedDependencyCollection<PsAndroidArtifact, PsAndroidModule, PsResolvedLibraryAndroidDependency, PsResolvedModuleAndroidDependency>(
  artifact,
  artifact.parent.parent
) {

  internal val reverseDependencies: Map<PsLibraryKey, Set<ReverseDependency>>

  init {
    reverseDependencies = collectReverseDependencies()
  }

  override fun collectResolvedDependencies(container: PsAndroidArtifact) {
    val artifact = container
    val resolvedArtifact = artifact.resolvedModel ?: return
    val dependencies = resolvedArtifact.level2Dependencies

    for (androidLibrary in dependencies.androidLibraries) {
      addLibrary(androidLibrary, artifact)
    }

    for (moduleLibrary in dependencies.moduleDependencies) {
      val gradlePath = moduleLibrary.projectPath
      if (gradlePath != null) {
        val module = artifact.parent.parent.parent.findModuleByGradlePath(gradlePath)
        // TODO(solodkyy): Support not yet resolved modules.
        if (module != null) {
          addModule(module, artifact, moduleLibrary.variant)
        }
      }
    }
    for (javaLibrary in dependencies.javaLibraries) {
      addLibrary(javaLibrary, artifact)
    }
  }

  private fun collectReverseDependencies(): Map<PsLibraryKey, Set<ReverseDependency>> {
    return libraryDependenciesBySpec
      .values()
      .flatMap { resolvedDependency ->
        resolvedDependency.pomDependencies.mapNotNull { transitiveDependencyTargetSpec ->
          libraryDependenciesBySpec[transitiveDependencyTargetSpec.toLibraryKey()]?.singleOrNull()?.let { pomResolvedDependency ->
            ReverseDependency.Transitive(pomResolvedDependency.spec, resolvedDependency, transitiveDependencyTargetSpec)
          }
        } +
        parent.dependencies.findLibraryDependencies(resolvedDependency.spec.toLibraryKey())
          .filter { declaredDependency -> artifact.contains(declaredDependency.parsedModel) }
          .map { declaredDependency -> ReverseDependency.Declared(resolvedDependency.spec, declaredDependency) }
      }
      .groupBy({ it.spec.toLibraryKey() })
      .mapValues { it.value.toSet() }
  }

  private fun addLibrary(library: Library, artifact: PsAndroidArtifact) {
    val declaredDependencies = mutableListOf<PsDeclaredLibraryAndroidDependency>()
    // TODO(solodkyy): Inverse the process and match parsed dependencies with resolved instead. (See other TODOs).
    val parsedDependencies = parent.dependencies

    val coordinates = GradleCoordinate.parseCoordinateString(library.artifactAddress)
    if (coordinates != null) {
      val spec = PsArtifactDependencySpec.create(coordinates)
      // TODO(b/74425541): Make sure it returns all the matching parsed dependencies rather than the first one.
      val matchingDeclaredDependencies =
        parsedDependencies
          .findLibraryDependencies(coordinates.groupId, coordinates.artifactId!!)
          .filter { artifact.contains(it.parsedModel) }
      // TODO(b/74425541): Reconsider duplicates.
      declaredDependencies.addAll(matchingDeclaredDependencies)
      val androidDependency = PsResolvedLibraryAndroidDependency(parent, this, spec, artifact, declaredDependencies)
      androidDependency.setDependenciesFromPomFile(parent.parent.pomDependencyCache.getPomDependencies(library.artifact))
      libraryDependenciesBySpec.put(androidDependency.spec.toLibraryKey(), androidDependency)
    }
  }

  private fun addModule(module: PsModule, artifact: PsAndroidArtifact, projectVariant: String?) {
    val gradlePath = module.gradlePath!!
    val matchingParsedDependency =
      parent
        .dependencies
        .findModuleDependencies(gradlePath)
        .filter { artifact.contains(it.parsedModel) }
    val dependency =
      PsResolvedModuleAndroidDependency(
        parent,
        gradlePath,
        ImmutableList.of(artifact),
        projectVariant,
        module,
        matchingParsedDependency)
    moduleDependenciesByGradlePath.put(gradlePath, dependency)
    // else we have a resolved dependency on a removed module (or composite build etc.).
  }
}
