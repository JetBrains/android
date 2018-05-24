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

import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.templates.IdeGoogleMavenRepository
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import java.util.*

class GradleModuleSystem(val module: Module, @TestOnly private val mavenRepository: GoogleMavenRepository = IdeGoogleMavenRepository)
  : AndroidModuleSystem {

  override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    // Check for android library dependencies from the build model
    val androidModuleModel = AndroidModuleModel.get(module) ?: throw DependencyManagementException(
      "Could not find android module model for module $module",
      DependencyManagementException.ErrorCodes.BUILD_SYSTEM_NOT_READY)

    return androidModuleModel.selectedMainCompileLevel2Dependencies.androidLibraries
      .asSequence()
      .mapNotNull { GradleCoordinate.parseCoordinateString(it.artifactAddress) }
      .find { it.matches(coordinate) }
  }

  override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    // Check for compile dependencies from the gradle build file
    val configurationName = GradleUtil.mapConfigurationName(CommonConfigurationNames.COMPILE,
                                                            GradleUtil.getAndroidGradleModelVersionInUse(module), false)
    return GradleBuildModel.get(module)?.let {
      it.dependencies().artifacts(configurationName)
        .asSequence()
        .mapNotNull { GradleCoordinate.parseCoordinateString("${it.group()}:${it.name().forceString()}:${it.version()}") }
        .find { it.matches(coordinate) }
    }
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

  private fun parseDependencyVersion(version: String?): GradleDependencyVersion {
    if (version == null) return GradleDependencyVersion(null)
    return GradleDependencyVersion(GradleVersion.parse(version))
  }
}
