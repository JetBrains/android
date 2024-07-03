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
package com.android.tools.idea.gradle.util

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.DynamicAppFeatureOnFeatureToken
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import java.util.stream.Collectors
import java.util.stream.Stream

class DynamicAppFeatureOnFeatureGradleToken : DynamicAppFeatureOnFeatureToken<GradleProjectSystem>, GradleToken {
  override fun getFeatureModulesDependingOnFeature(projectSystem: GradleProjectSystem, module: Module): List<Module> {
    return if (!StudioFlags.SUPPORT_FEATURE_ON_FEATURE_DEPS.get()) {
      listOf()
    }
    else selectFeatureModules(removeModulesInTheSameGradleProject(
      ModuleManager.getInstance(module.project).getModuleDependentModules(module.getMainModule())
        .stream(), module))

  }

  override fun getFeatureModuleDependenciesForFeature(projectSystem: GradleProjectSystem, module: Module): List<Module> {
    return if (!StudioFlags.SUPPORT_FEATURE_ON_FEATURE_DEPS.get()) {
      listOf()
    }
    else selectFeatureModules(removeModulesInTheSameGradleProject(
      Stream.of(*ModuleRootManager.getInstance(module.getMainModule()).dependencies), module))
}

  /**
   * Finds the modules in a stream that are either legacy or dynamic features. If there are multiple modules belonging to the same
   * dynamic feature (i.e Gradle Project) this method will only return the holder modules.
   */
  private fun selectFeatureModules(moduleStream: Stream<Module>): List<Module> {
    return moduleStream.map { it.getHolderModule() }
      .distinct()
      .filter { module: Module ->
        val moduleSystem = module.getModuleSystem()
        val type = moduleSystem.type
        type === AndroidModuleSystem.Type.TYPE_FEATURE ||  // Legacy
        type === AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE
      }.collect(Collectors.toList())
  }

  private fun removeModulesInTheSameGradleProject(modules: Stream<Module>, moduleOfProjectToRemove: Module): Stream<Module> {
    return modules.filter { m: Module -> m.getHolderModule() !== moduleOfProjectToRemove.getHolderModule() }
  }


}