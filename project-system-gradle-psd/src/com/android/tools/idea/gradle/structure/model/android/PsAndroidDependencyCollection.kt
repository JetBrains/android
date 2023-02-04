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

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnknownLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedModuleLibrary
import com.android.tools.idea.gradle.model.projectPath
import com.android.tools.idea.gradle.model.variant
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryKey
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedDependencyCollection
import com.android.tools.idea.gradle.structure.model.matchJarDeclaredDependenciesIn
import com.android.tools.idea.gradle.structure.model.relativeFile
import com.android.tools.idea.gradle.structure.model.toLibraryKey


/**
 * A collection of dependencies of [parent] Android module.
 */
interface PsAndroidDependencyCollection<out LibraryDependencyT, out JarDependencyT, out ModuleDependencyT>
  : PsDependencyCollection<PsAndroidModule, LibraryDependencyT, JarDependencyT, ModuleDependencyT>
  where LibraryDependencyT : PsAndroidDependency,
        LibraryDependencyT : PsLibraryDependency,
        JarDependencyT : PsAndroidDependency,
        JarDependencyT : PsJarDependency,
        ModuleDependencyT : PsAndroidDependency,
        ModuleDependencyT : PsModuleDependency
{
  override val items: List<PsAndroidDependency> get() = modules + libraries + jars
}

/**
 * A collection of parsed (configured) dependencies of [parent] module.
 */
class PsAndroidModuleDependencyCollection(parent: PsAndroidModule)
  : PsDeclaredDependencyCollection<PsAndroidModule, PsDeclaredLibraryAndroidDependency, PsDeclaredJarAndroidDependency, PsDeclaredModuleAndroidDependency>(
  parent
), PsAndroidDependencyCollection<PsDeclaredLibraryAndroidDependency, PsDeclaredJarAndroidDependency, PsDeclaredModuleAndroidDependency> {

  override fun createOrUpdateLibraryDependency(
    existing: PsDeclaredLibraryAndroidDependency?,
    artifactDependencyModel: ArtifactDependencyModel
  ): PsDeclaredLibraryAndroidDependency =
    (existing ?: PsDeclaredLibraryAndroidDependency(parent)).apply {init(artifactDependencyModel)}

  override fun createOrUpdateJarFileDependency(
    existing: PsDeclaredJarAndroidDependency?,
    fileDependencyModel: FileDependencyModel
  ): PsDeclaredJarAndroidDependency =
    (existing ?: PsDeclaredJarAndroidDependency(parent)).apply {init(fileDependencyModel)}

  override fun createOrUpdateJarFileTreeDependency(
    existing: PsDeclaredJarAndroidDependency?,
    fileTreeDependencyModel: FileTreeDependencyModel
  ): PsDeclaredJarAndroidDependency =
    (existing ?: PsDeclaredJarAndroidDependency(parent)).apply {init(fileTreeDependencyModel)}

  override fun createOrUpdateModuleDependency(
    existing: PsDeclaredModuleAndroidDependency?,
    moduleDependencyModel: ModuleDependencyModel
  ): PsDeclaredModuleAndroidDependency =
    (existing ?: PsDeclaredModuleAndroidDependency(parent)).apply {init(moduleDependencyModel)}
}

/**
 * A collection of resolved dependencies of a specific [artifact] of module [parent].
 */
class PsAndroidArtifactDependencyCollection(val artifact: PsAndroidArtifact)
  : PsResolvedDependencyCollection<PsAndroidArtifact, PsAndroidModule, PsResolvedLibraryAndroidDependency,
  PsResolvedJarAndroidDependency, PsResolvedModuleAndroidDependency>(
  artifact,
  artifact.parent.parent
), PsAndroidDependencyCollection<PsResolvedLibraryAndroidDependency, PsResolvedJarAndroidDependency, PsResolvedModuleAndroidDependency> {

  internal val reverseDependencies: Map<PsLibraryKey, Set<ReverseDependency>>

  init {
    reverseDependencies = collectReverseDependencies()
  }

  override fun collectResolvedDependencies(container: PsAndroidArtifact) {
    val artifact = container
    val resolvedArtifact = artifact.resolvedModel ?: return
    val dependencies = resolvedArtifact.compileClasspath

    dependencies.unresolvedDependencies.forEach { unresolvedDependency ->
      val libraries = dependencies.resolver.resolve(unresolvedDependency)
      libraries.forEach { library ->
        when (library) {
          is IdeAndroidLibrary -> {
            addLibrary(library, artifact)
          }
          is IdeJavaLibrary -> {
            addLibrary(library, artifact)
          }
          is IdeModuleLibrary -> {
            val gradlePath = library.projectPath
            val module = artifact.parent.parent.parent.findModuleByGradlePath(gradlePath)
            // TODO(solodkyy): Support not yet resolved modules.
            if (module != null) {
              addModule(module, artifact, library.variant)
            }
          }
          is IdeUnknownLibrary -> Unit // Not Handled
        }
      }
    }
  }

  private fun collectReverseDependencies(): Map<PsLibraryKey, Set<ReverseDependency>> {
    return libraries
      .flatMap { resolvedDependency ->
        resolvedDependency.pomDependencies.mapNotNull { transitiveDependencyTargetSpec ->
          findLibraryDependencies(transitiveDependencyTargetSpec.toLibraryKey()).singleOrNull()?.let { pomResolvedDependency ->
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

  private fun addLibrary(library: IdeArtifactLibrary, artifact: PsAndroidArtifact) {
    val libraryArtifactFile = when (library) {
      is IdeAndroidLibrary -> library.artifact
      is IdeJavaLibrary -> library.artifact
    }
    // TODO(solodkyy): Inverse the process and match parsed dependencies with resolved instead. (See other TODOs).
    val parsedDependencies = parent.dependencies

    val coordinates = GradleCoordinate.parseCoordinateString(library.artifactAddress)
    if (coordinates != null) {
      val spec = PsArtifactDependencySpec.create(coordinates)
      // TODO(b/74425541): Make sure it returns all the matching parsed dependencies rather than the first one.
      val matchingDeclaredDependencies =
        parsedDependencies
          .findLibraryDependencies(coordinates.groupId, coordinates.artifactId)
          .filter { artifact.contains(it.parsedModel) }
      // TODO(b/74425541): Reconsider duplicates.
      val androidDependency = PsResolvedLibraryAndroidDependency(parent, this, spec, artifact, matchingDeclaredDependencies)
      if (libraryArtifactFile != null) {
        androidDependency.setDependenciesFromPomFile(
          parent.parent.pomDependencyCache.getPomDependencies(
            library.artifactAddress,
            libraryArtifactFile
          )
        )
      }
      addLibraryDependency(androidDependency)
    }
    else {
      val artifactCanonicalFile = libraryArtifactFile?.canonicalFile
      if (artifactCanonicalFile != null) {
        val matchingDeclaredDependencies =
          matchJarDeclaredDependenciesIn(parsedDependencies, artifactCanonicalFile)
        val path = parent.relativeFile(artifactCanonicalFile)
        val jarDependency = PsResolvedJarAndroidDependency(parent, this, path.path, artifact, matchingDeclaredDependencies)
        addJarDependency(jarDependency)
      }
    }
  }

  private fun addModule(module: PsModule, artifact: PsAndroidArtifact, projectVariant: String?) {
    val gradlePath = module.gradlePath
    val matchingParsedDependency =
      parent
        .dependencies
        .findModuleDependencies(gradlePath)
        .filter { artifact.contains(it.parsedModel) }
    val dependency =
      PsResolvedModuleAndroidDependency(
        parent,
        gradlePath,
        artifact,
        projectVariant,
        module,
        matchingParsedDependency)
    addModuleDependency(dependency)
  }
}
