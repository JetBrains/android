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

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel
import com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.projectsystem.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

class GradleModuleSystem(val module: Module) : AndroidModuleSystem {
  /**
   * This method will add the ":+" to the given dependency.
   * For Guava, for example: the dependency coordinate will not include the version:
   * com.google.guava:guava
   * and this method will add "+" as the version of the dependency to add.
   * @param dependency The dependency dependency without version.
   */
  override fun addDependency(dependency: String) {
    val manager = GradleDependencyManager.getInstance(module.project)
    val coordinate = GradleCoordinate.parseCoordinateString(dependency + ":+")
    if (coordinate != null) {
      manager.addDependencies(module, listOf(coordinate), null)
    }
  }

  override fun getResolvedVersion(artifactId: GoogleMavenArtifactId): GoogleMavenArtifactVersion? {
    // Check for android library dependencies from the build model
    val androidModuleModel = AndroidModuleModel.get(module) ?:
        throw DependencyManagementException("Could not find android module model for module $module",
            DependencyManagementException.ErrorCodes.BUILD_SYSTEM_NOT_READY)

    return androidModuleModel.selectedMainCompileLevel2Dependencies.androidLibraries
        .asSequence()
        .mapNotNull { GradleCoordinate.parseCoordinateString(it.artifactAddress) }
        .find { "${it.groupId}:${it.artifactId}" == artifactId.toString() }
        ?.let { GradleDependencyVersion(it.version) }
  }

  override fun getDeclaredVersion(artifactId: GoogleMavenArtifactId): GoogleMavenArtifactVersion? {
    // Check for compile dependencies from the gradle build file
    val configurationName = GradleUtil.mapConfigurationName(CommonConfigurationNames.COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false)

    return GradleBuildModel.get(module)?.let {
      it.dependencies().artifacts(configurationName)
          .filter { artifactId.toString() == "${it.group().value()}:${it.name().value()}" }
          .map { parseDependencyVersion(it.version().value()) }
          .firstOrNull()
    }
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

  private fun parseDependencyVersion(version: String?): GradleDependencyVersion {
    if (version == null) return GradleDependencyVersion(null)
    return GradleDependencyVersion(GradleVersion.parse(version))
  }
}