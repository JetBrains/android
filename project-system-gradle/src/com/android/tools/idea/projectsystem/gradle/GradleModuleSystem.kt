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
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.IdeGoogleMavenRepository
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import java.util.Collections
import java.util.function.Predicate

class GradleModuleSystem(val module: Module, @TestOnly private val mavenRepository: GoogleMavenRepository = IdeGoogleMavenRepository) :
  AndroidModuleSystem,
  ClassFileFinder by GradleClassFileFinder(module),
  SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module) {

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
    // versions, never wildcards. Platform-support libs need to use the exact same revision string including wildcards.
    if (isPlatformSupportLibrary(GradleCoordinate(mavenGroupId, mavenArtifactId, "+"))) {
      val supportLibVersion = getExistingPlatformSupportLibraryVersion()
      if (supportLibVersion != null) {
        return GradleCoordinate.parseCoordinateString("$mavenGroupId:$mavenArtifactId:$supportLibVersion")
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

  private fun isPlatformSupportLibrary(coordinate: GradleCoordinate) =
    GoogleMavenArtifactId.forCoordinate(coordinate)?.isPlatformSupportLibrary ?: false

  private fun getExistingPlatformSupportLibraryVersion(): GradleVersion? =
    GoogleMavenArtifactId.values()
      .asSequence()
      .filter { it.isPlatformSupportLibrary }
      .mapNotNull { getRegisteredDependency(it.getCoordinate("+")) }
      .mapNotNull { it.version }
      .firstOrNull()
}
