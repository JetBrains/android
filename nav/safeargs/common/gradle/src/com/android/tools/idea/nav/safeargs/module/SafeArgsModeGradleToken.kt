/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.module

import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.gradleModuleModel
import com.android.tools.idea.nav.safeargs.SafeArgsFeature
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.module.Module

class SafeArgsModeGradleToken : SafeArgsModeToken<GradleProjectSystem>, GradleToken {
  override fun getSafeArgsMode(projectSystem: GradleProjectSystem, module: Module): SafeArgsMode =
    module.gradleModuleModel?.toSafeArgsMode() ?: SafeArgsMode.NONE

  override fun getSafeArgsFeatures(
    projectSystem: GradleProjectSystem,
    module: Module,
  ): Set<SafeArgsFeature> {
    val moduleSystem = projectSystem.getModuleSystem(module)
    val component =
      moduleSystem.getResolvedDependency(
        GoogleMavenArtifactId.ANDROIDX_NAVIGATION_COMMON.getModule(),
        DependencyScopeType.MAIN,
      )
        ?: moduleSystem.getResolvedDependency(
          GoogleMavenArtifactId.ANDROIDX_NAVIGATION_COMMON_ANDROID.getModule(),
          DependencyScopeType.MAIN,
        )
    return component?.version?.toSafeArgsFeatures() ?: setOf()
  }

  private fun Version.toSafeArgsFeatures(): Set<SafeArgsFeature> =
    setOfNotNull(
      SafeArgsFeature.FROM_SAVED_STATE_HANDLE.takeIf { this >= Version.parse("2.4.0-alpha01") },
      SafeArgsFeature.TO_SAVED_STATE_HANDLE.takeIf { this >= Version.parse("2.4.0-alpha07") },
      SafeArgsFeature.ADJUST_PARAMS_WITH_DEFAULTS.takeIf { this >= Version.parse("2.4.0-alpha08") },
    )

  private fun GradleModuleModel.toSafeArgsMode(): SafeArgsMode {
    return when {
      safeArgsKotlin -> SafeArgsMode.KOTLIN
      safeArgsJava -> SafeArgsMode.JAVA
      else -> SafeArgsMode.NONE
    }
  }
}
