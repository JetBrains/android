/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.execution.common.debug.utils

import com.android.tools.idea.projectsystem.AndroidModuleSystem.Type.TYPE_LIBRARY
import com.android.tools.idea.projectsystem.AndroidModuleSystem.Type.TYPE_TEST
import com.android.tools.idea.projectsystem.CommonTestType
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.gradle.getAndroidTestModule
import com.android.tools.idea.projectsystem.gradle.getMainModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.gradle.isAndroidTestModule
import com.android.tools.idea.projectsystem.sourceProviders
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

class FacetFinderGradleToken : FacetFinderToken<GradleProjectSystem>, GradleToken {
  override fun findGlobalProcessDefinition(projectSystem: GradleProjectSystem, project: Project, processName: String): Module? {
    // Iterate over holder modules, looking into candidate source providers of
    // those holders and mapping to sourceSet modules "by hand".
    for (facet in project.getAndroidFacets()) {
      for (sourceProvider in facet.sourceProviders.currentSourceProviders) {
        for (manifestFile in sourceProvider.manifestFiles) {
          val globalProcessNames = ProcessNameReader.readGlobalProcessNames(project, manifestFile)
          if (globalProcessNames.contains(processName)) {
            return facet.module.getMainModule()
          }
        }
      }
      for (sourceProvider in facet.sourceProviders.currentDeviceTestSourceProviders[CommonTestType.ANDROID_TEST] ?: emptyList()) {
        for (manifestFile in sourceProvider.manifestFiles) {
          val globalProcessNames = ProcessNameReader.readGlobalProcessNames(project, manifestFile)
          if (globalProcessNames.contains(processName)) {
            return facet.module.getAndroidTestModule()
          }
        }
      }
    }
    return null
  }

  override fun isDirectlyDeployable(projectSystem: GradleProjectSystem, project: Project, module: Module): Boolean =
    module.isAndroidTestModule() || module.getModuleSystem().type != TYPE_LIBRARY

  override fun hasTestNature(projectSystem: GradleProjectSystem, project: Project, module: Module): Boolean =
    module.isAndroidTestModule() || module.getModuleSystem().type == TYPE_TEST
}