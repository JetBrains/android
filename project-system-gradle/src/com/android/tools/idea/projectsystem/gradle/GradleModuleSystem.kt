/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.SdkConstants
import com.android.ide.common.gradle.model.GradleModelConverter
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.projectmodel.Library
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider
import com.android.tools.idea.templates.IdeGoogleMavenRepository
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import java.util.ArrayDeque
import java.util.Collections
import java.util.function.Predicate

class GradleModuleSystem(val module: Module, @TestOnly private val mavenRepository: GoogleMavenRepository = IdeGoogleMavenRepository) :
  AndroidModuleSystem,
  ClassFileFinder by GradleClassFileFinder(module),
  SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module) {

  private val groupsWithVersionIdentifyRequirements = listOf(SdkConstants.SUPPORT_LIB_GROUP_ID)

  override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    return getResolvedDependentLibraries()
      .asSequence()
      .mapNotNull { GradleCoordinate.parseCoordinateString(it.address) }
      .find { it.matches(coordinate) }
  }

  override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    val artifacts = ProjectBuildModel.get(module.project).getModuleBuildModel(module)?.dependencies()?.artifacts() ?: return null
    return artifacts
      .asSequence()
      .mapNotNull { GradleCoordinate.parseCoordinateString("${it.group()}:${it.name().forceString()}:${it.version()}") }
      .find { it.matches(coordinate) }
  }

  override fun getResolvedDependentLibraries(): Collection<Library> {
    val gradleModel = AndroidModuleModel.get(module) ?: return emptySet()

    val converter = GradleModelConverter(gradleModel.androidProject)
    val javaLibraries = gradleModel.selectedMainCompileLevel2Dependencies.javaLibraries.mapNotNull(converter::convert)
    val androidLibraries = gradleModel.selectedMainCompileLevel2Dependencies.androidLibraries.mapNotNull(converter::convert)

    return javaLibraries + androidLibraries
  }

  override fun registerDependency(coordinate: GradleCoordinate) {
    GradleDependencyManager.getInstance(module.project).addDependenciesWithoutSync(module, Collections.singletonList(coordinate))
  }

  override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
    return GradleAndroidModuleTemplate.getModuleTemplates(module, targetDirectory)
  }

  override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
    return supportsPngGeneration(module)
  }

  override fun getInstantRunSupport(): CapabilityStatus {
    return getInstantRunCapabilityStatus(module)
  }

  override fun getLatestCompatibleDependency(mavenGroupId: String, mavenArtifactId: String): GradleCoordinate? {
    // This special edge-case requires it's own if-block because IdeGoogleMavenRepository will only return compatible and resolved
    // versions, never wildcards. Some libraries need to use the exact same revision string including wildcards.
    if (groupsWithVersionIdentifyRequirements.contains(mavenGroupId)) {
      val existingVersion = findVersionOfExistingGroupDependency(mavenGroupId)
      if (existingVersion != null) {
        return GradleCoordinate.parseCoordinateString("$mavenGroupId:$mavenArtifactId:$existingVersion")
      }
    }

    // For now this always return true to allow every version. Logic for versioning platform-support libs was taken out because
    // IdeGoogleMavenRepository will never return a coordinate that satisfies the specific requirements on platform-support libs
    // where the exact registered revision string must be the same.
    val versionPredicate: Predicate<GradleVersion> = Predicate { true; }
    val foundVersion = mavenRepository.findVersion(mavenGroupId, mavenArtifactId, versionPredicate, false)
                       ?: mavenRepository.findVersion(mavenGroupId, mavenArtifactId, versionPredicate, true)
                       ?: return null
    return GradleCoordinate.parseCoordinateString("$mavenGroupId:$mavenArtifactId:$foundVersion")
  }

  // Find an existing artifact of this group and return the version.
  // Search for matching dependency artifacts in:
  // 1) The current module
  // 2) The transitive dependencies of the current module
  // 3) The modules which transitively depends on the current module
  //
  // Note: it is NOT ok to check all modules in a project, since there may be independent modules present.
  // Such as a wear module and a phone module.
  @VisibleForTesting
  fun findVersionOfExistingGroupDependency(mavenGroupId: String): GradleVersion? {
    val foundInModule = findVersionOfExistingGroupDependencyInModule(mavenGroupId)
    if (foundInModule != null) {
      return foundInModule
    }
    val nameLookup = HashMap<String, Module>()
    ModuleManager.getInstance(module.project).modules.forEach { nameLookup[moduleReference(it.name)] = it }
    val dependencyLookup = ArrayListMultimap.create<String, String>()
    val reverseDependencyLookup = ArrayListMultimap.create<String, String>()
    nameLookup.values.forEach { findDependencies(it, dependencyLookup, reverseDependencyLookup) }
    val foundInDependencies = findVersionFromDependencies(mavenGroupId, dependencyLookup, nameLookup)
    if (foundInDependencies != null) {
      return foundInDependencies
    }
    return findVersionFromDependencies(mavenGroupId, reverseDependencyLookup, nameLookup)
  }

  private fun findDependencies(module: Module, dependencies: Multimap<String, String>, reverseDependencies: Multimap<String, String>) {
    val projectModel = ProjectBuildModel.get(module.project)
    val dependentNames = projectModel.getModuleBuildModel(module)?.dependencies()?.modules()?.map { it.path().forceString() } ?: return
    val moduleReference = moduleReference(module.name)
    dependencies.putAll(moduleReference, dependentNames)
    dependentNames.forEach { reverseDependencies.put(it, moduleReference) }
  }

  private fun findVersionFromDependencies(mavenGroupId: String,
                                          dependenciesLookup: Multimap<String, String>,
                                          nameLookup: Map<String, Module>): GradleVersion? {
    return findTransitiveClosure(dependenciesLookup).stream()
      .map { nameLookup[it]?.getModuleSystem() as? GradleModuleSystem }
      .map { it?.findVersionOfExistingGroupDependencyInModule(mavenGroupId) }
      .filter { it != null }
      .findAny().orElse(null)
  }

  private fun findTransitiveClosure(lookup: Multimap<String, String>): Set<String> {
    val currentModuleReference = moduleReference(module.name)
    val references = lookup[currentModuleReference] ?: return emptySet()
    val result = HashSet<String>()
    val stack = ArrayDeque<String>(references)
    while (stack.isNotEmpty()) {
      val element = stack.pop()
      result.add(element)
      lookup[element]?.stream()?.filter { !result.contains(it) }?.forEach { stack.add(it) }
    }
    return result
  }

  private fun moduleReference(moduleName: String): String {
    return ":$moduleName"
  }

  private fun findVersionOfExistingGroupDependencyInModule(mavenGroupId: String): GradleVersion? {
    return ProjectBuildModel.get(module.project).getModuleBuildModel(module)?.dependencies()?.artifacts()
      ?.map { GradleCoordinate.parseCoordinateString(it.compactNotation()) }
      ?.firstOrNull { it?.groupId == mavenGroupId }?.version
  }
}
