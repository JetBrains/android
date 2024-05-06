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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.module.Module

class SafeArgsModeGradleToken : SafeArgsModeToken<GradleProjectSystem>, GradleToken {
  override fun getSafeArgsMode(projectSystem: GradleProjectSystem, module: Module): SafeArgsMode {
    val gradleFacet = GradleFacet.getInstance(module)
    return gradleFacet?.gradleModuleModel?.toSafeArgsMode() ?: SafeArgsMode.NONE
  }

  private fun GradleModuleModel.toSafeArgsMode(): SafeArgsMode {
    return when {
      hasSafeArgsKotlinPlugin() -> SafeArgsMode.KOTLIN
      hasSafeArgsJavaPlugin() -> SafeArgsMode.JAVA
      else -> SafeArgsMode.NONE
    }
  }
}
