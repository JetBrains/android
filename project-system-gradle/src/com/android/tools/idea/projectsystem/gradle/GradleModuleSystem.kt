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
import com.android.ide.common.util.PathString
import com.android.projectmodel.AarLibrary
import com.android.projectmodel.JavaLibrary
import com.android.projectmodel.Library
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

class GradleModuleSystem(val module: Module) : AndroidModuleSystem, ClassFileFinder by GradleClassFileFinder(module) {

  override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    return getDependentLibraries()
      .asSequence()
      .mapNotNull { GradleCoordinate.parseCoordinateString(it.address) }
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

  override fun getDependentLibraries(): Collection<Library> {
    val gradleModel = AndroidModuleModel.get(module) ?: return emptySet()

    val javaLibraries = gradleModel.selectedMainCompileLevel2Dependencies.javaLibraries.map { library ->
        JavaLibrary(
          address = library.artifactAddress,
          classesJar = PathString(library.artifact)
        )
      }

    val androidLibraries = gradleModel.selectedMainCompileLevel2Dependencies.androidLibraries.map { library ->
        AarLibrary(
          address = library.artifactAddress,
          location = PathString(library.artifact),
          manifestFile = PathString(library.manifest),
          classesJar = PathString(library.jarFile),
          dependencyJars = library.localJars.map(::PathString),
          resFolder = PathString(library.resFolder),
          symbolFile = PathString(library.symbolFile),
          resApkFile = library.resStaticLibrary?.let(::PathString)
        )
      }

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
}
