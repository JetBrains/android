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
import com.intellij.openapi.module.Module
import java.util.function.Consumer

class PsAndroidDependencyCollection(private val parent: PsAndroidModule) : PsModelCollection<PsAndroidDependency> {

  private val moduleDependenciesByGradlePath = mutableMapOf<String, PsModuleAndroidDependency>()
  private val libraryDependenciesBySpec = mutableMapOf<String, PsLibraryAndroidDependency>()

  init {
    collectDependencies()
  }

  private fun collectDependencies() {
    collectResolvedDependencies()
    collectParsedDependencies()
  }

  private fun collectParsedDependencies() {
    collectParsedDependencies(parent.parsedDependencies)
  }

  private fun collectResolvedDependencies() {
    parent.forEachVariant {
      it.forEachArtifact { collectResolvedDependencies(it) }
    }
  }

  private fun collectParsedDependencies(parsedDependencies: PsParsedDependencies) {
    val artifactsByConfigurationNames = buildArtifactsByConfigurations()
    parsedDependencies.forEachLibraryDependency { libraryDependency ->
      artifactsByConfigurationNames[libraryDependency.configurationName()]?.forEach { artifact ->
        addLibrary(
          null,
          PsArtifactDependencySpec(
            libraryDependency.name().value(),
            libraryDependency.group().value(),
            // TODO(solodkyy): Properly handle wildcard versions.
            libraryDependency.version().value()
          ),
          artifact,
          libraryDependency
        )

      }
    }
    parsedDependencies.forEachModuleDependency { moduleDependency ->
      artifactsByConfigurationNames[moduleDependency.configurationName()]?.forEach { artifact ->
        // TODO(solodkyy): Handle pre AGP 3.0 configurations.
        addModule(
          moduleDependency.path().value(),
          artifact,
          moduleDependency.configuration().value(),
          moduleDependency
        )
      }
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

  private fun addModule(gradlePath: String, artifact: PsAndroidArtifact, projectVariant: String?) {
    val parsedDependencies = parent.parsedDependencies

    val matchingParsedDependency =
      parsedDependencies.findModuleDependency(gradlePath) { parsedDependency: DependencyModel -> artifact.contains(parsedDependency) }

    addModule(gradlePath, artifact, projectVariant, matchingParsedDependency)
  }

  private fun addModule(
    gradlePath: String,
    artifact: PsAndroidArtifact,
    projectVariant: String?,
    matchingParsedDependency: ModuleDependencyModel?
  ) {
    val dependency = getOrCreateModuleDependency(gradlePath, artifact, projectVariant, matchingParsedDependency)
    updateDependency(dependency, artifact, matchingParsedDependency)
  }

  private fun getOrCreateModuleDependency(
    gradlePath: String,
    artifact: PsAndroidArtifact,
    projectVariant: String?,
    matchingParsedDependency: ModuleDependencyModel?
  ): PsModuleAndroidDependency {
    val module = parent.parent.findModuleByGradlePath(gradlePath)
    val resolvedModule = module?.resolvedModel
    var dependency = findElement(gradlePath, PsModuleAndroidDependency::class.java)
    if (dependency == null) {
      dependency = PsModuleAndroidDependency(parent, gradlePath, artifact, projectVariant, resolvedModule, matchingParsedDependency)
      moduleDependenciesByGradlePath[gradlePath] = dependency
    }
    return dependency
  }

  private fun addLibrary(library: Library, artifact: PsAndroidArtifact) {
    // TODO(solodkyy): Inverse the process and match parsed dependencies with resolved instead. (See other TODOs).
    val parsedDependencies = parent.parsedDependencies

    val coordinates = GradleCoordinate.parseCoordinateString(library.artifactAddress)

    if (coordinates != null) {
      val spec = PsArtifactDependencySpec.create(coordinates)

      val matchingParsedDependency =
        parsedDependencies.findLibraryDependency(coordinates) { parsedDependency: DependencyModel -> artifact.contains(parsedDependency) }
      if (matchingParsedDependency != null) {
        val parsedVersionValue = matchingParsedDependency.version().value()
        if (parsedVersionValue != null) {
          // The dependency has a version in the build.gradle file.
          // "tryParse" just in case the build.file has an invalid version.
          val parsedVersion = GradleVersion.tryParse(parsedVersionValue)

          val versionFromGradle = GradleVersion.parse(coordinates.revision)
          if (parsedVersion != null && versionsMatch(parsedVersion, versionFromGradle)) {
            // Match.
            addLibrary(library, spec, artifact, matchingParsedDependency)
          } else {
            // Version mismatch. This can happen when the project specifies an artifact version but Gradle uses a different version
            // from a transitive dependency.
            // Example:
            // 1. Module 'app' depends on module 'lib'
            // 2. Module 'app' depends on Guava 18.0
            // 3. Module 'lib' depends on Guava 19.0
            // Gradle will force module 'app' to use Guava 19.0

            // This is a case that may look as a version mismatch:
            //
            // testCompile 'junit:junit:4.11+'
            // androidTestCompile 'com.android.support.test.espresso:espresso-core:2.2.1'
            //
            // Here 'espresso' brings junit 4.12, but there is no mismatch with junit 4.11, because they are in different artifacts.
            var potentialDuplicate: PsLibraryAndroidDependency? = null
            for (dependency in libraryDependenciesBySpec.values) {
              if (dependency.parsedModels.contains(matchingParsedDependency)) {
                potentialDuplicate = dependency
                break
              }
            }

            if (potentialDuplicate != null) {
              // TODO match ArtifactDependencyModel#configurationName with potentialDuplicate.getContainers().artifact
            }

            // Create the dependency model that will be displayed in the "Dependencies" table.
            addLibrary(library, spec, artifact, matchingParsedDependency)

            // Create a dependency model for the transitive dependency, so it can be displayed in the "Variants" tool window.
            addLibrary(library, spec, artifact, null)
          }
        }
      } else {
        // This dependency was not declared, it could be a transitive one.
        addLibrary(library, spec, artifact, null)
      }
    }
  }

  private fun addLibrary(
    library: Library?,
    resolvedSpec: PsArtifactDependencySpec,
    artifact: PsAndroidArtifact,
    parsedModel: ArtifactDependencyModel?
  ) {
    val dependency = getOrCreateLibraryDependency(resolvedSpec, library, artifact, parsedModel)
    updateDependency(dependency, artifact, parsedModel)
  }

  private fun getOrCreateLibraryDependency(
    resolvedSpec: PsArtifactDependencySpec,
    library: Library?,
    artifact: PsAndroidArtifact,
    parsedModel: ArtifactDependencyModel?
  ): PsAndroidDependency {
    val compactNotation = resolvedSpec.toString()
    val dependency: PsLibraryAndroidDependency? = libraryDependenciesBySpec[compactNotation]
    return if (dependency != null) {
      if (library !== null && dependency.resolvedModel !== library) {
        throw IllegalStateException("Dependency $compactNotation cannot have two different resolved models.")
      }
      if (parsedModel != null) {
        dependency.addParsedModel(parsedModel)
      }
      dependency
    } else {
      val androidDependency = PsLibraryAndroidDependency(parent, resolvedSpec, artifact, library, parsedModel)
      libraryDependenciesBySpec[compactNotation] = androidDependency
      library?.artifact?.let {
        androidDependency.setDependenciesFromPomFile(findDependenciesInPomFile(it))
      }
      androidDependency
    }
  }

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

  fun forEachDeclaredDependency(consumer: Consumer<PsAndroidDependency>) {
    libraryDependenciesBySpec.values.filter { it.isDeclared }.forEach(consumer)
    moduleDependenciesByGradlePath.values.filter { it.isDeclared }.forEach(consumer)
  }

  fun forEachModuleDependency(consumer: Consumer<PsModuleAndroidDependency>) {
    moduleDependenciesByGradlePath.values.forEach(consumer)
  }

  fun addLibraryDependency(
    spec: PsArtifactDependencySpec,
    artifact: PsAndroidArtifact,
    parsedModel: ArtifactDependencyModel?
  ) {
    var dependency: PsLibraryAndroidDependency? = libraryDependenciesBySpec[spec.toString()]
    if (dependency == null) {
      dependency = PsLibraryAndroidDependency(parent, spec, artifact, null, parsedModel)
      libraryDependenciesBySpec[spec.toString()] = dependency
    } else {
      updateDependency(dependency, artifact, parsedModel)
    }
  }

  fun addModuleDependency(
    modulePath: String,
    artifact: PsAndroidArtifact,
    resolvedModel: Module?,
    parsedModel: ModuleDependencyModel?
  ) {
    var dependency: PsModuleAndroidDependency? = moduleDependenciesByGradlePath[modulePath]
    if (dependency == null) {
      dependency = PsModuleAndroidDependency(parent, modulePath, artifact, null, resolvedModel, parsedModel)
      moduleDependenciesByGradlePath[modulePath] = dependency
    } else {
      updateDependency(dependency, artifact, parsedModel)
    }
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

private fun updateDependency(
  dependency: PsAndroidDependency,
  artifact: PsAndroidArtifact,
  parsedModel: DependencyModel?
) {
  if (parsedModel != null) {
    dependency.addParsedModel(parsedModel)
  }
  dependency.addContainer(artifact)
}
