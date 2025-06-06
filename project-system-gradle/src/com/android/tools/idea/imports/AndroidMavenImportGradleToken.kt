/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.ide.common.gradle.Dependency
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.module.Module
import com.android.ide.common.gradle.Module as GradleModule

class AndroidMavenImportGradleToken : AndroidMavenImportToken<GradleProjectSystem>, GradleToken {
  override fun dependsOn(projectSystem: GradleProjectSystem, module: Module, artifact: String): Boolean {
    val moduleSystem = projectSystem.getModuleSystem(module)
    val gradleModule = GradleModule.tryParse(artifact) ?: return false
    return moduleSystem.getResolvedDependency(gradleModule, DependencyScopeType.MAIN) != null
  }

  override fun addDependency(projectSystem: GradleProjectSystem,
                             module: Module,
                             artifact: String,
                             version: String?,
                             type: DependencyType) {
    val moduleSystem = projectSystem.getModuleSystem(module)
    val dependency = when {
      version.isNullOrEmpty() -> Dependency.parse("$artifact:+")
      else -> Dependency.parse("$artifact:$version")
    }
    moduleSystem.registerDependency(dependency, type)
  }
}