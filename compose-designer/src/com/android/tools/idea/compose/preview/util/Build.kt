/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project

/**
 * Triggers the build of the given [modules] by calling the compile`Variant`Kotlin task
 */
private fun requestKotlinBuild(project: Project, modules: Set<Module>) {
  fun createBuildTasks(module: Module): String? {
    val gradlePath = GradleFacet.getInstance(module)?.configuration?.GRADLE_PROJECT_PATH ?: return null
    val currentVariant = AndroidModuleModel.get(module)?.selectedVariant?.name?.capitalize() ?: return null
    // We need to get the compileVariantKotlin task name. There is not direct way to get it from the model so, for now,
    // we just build it ourselves.
    // TODO(b/145199867): Replace this with the right API call to obtain compileVariantKotlin after the bug is fixed.
    return "${gradlePath}${SdkConstants.GRADLE_PATH_SEPARATOR}compile${currentVariant}Kotlin"
  }

  fun createBuildTasks(modules: Collection<Module>): Map<Module, List<String>> =
    modules
      .mapNotNull {
        Pair(it, listOf(createBuildTasks(it) ?: return@mapNotNull null))
      }
      .filter { it.second.isNotEmpty() }
      .toMap()

  val moduleFinder = ProjectStructure.getInstance(project).moduleFinder

  createBuildTasks(modules).forEach {
    val path = moduleFinder.getRootProjectPath(it.key)
    GradleBuildInvoker.getInstance(
      project).executeTasks(path.toFile(), it.value)
  }
}

/**
 * Triggers the build of the given [modules] by calling the compileSourcesDebug task
 */
private fun requestCompileJavaBuild(project: Project, modules: Set<Module>) =
  GradleBuildInvoker.getInstance(
    project).compileJava(modules.toTypedArray(),
                         TestCompileType.NONE)

internal fun requestBuild(project: Project, module: Module) {
  if (project.isDisposed || module.isDisposed) {
    return
  }

  val modules = mutableSetOf(module)
  ModuleUtil.collectModulesDependsOn(module, modules)

  // When COMPOSE_PREVIEW_ONLY_KOTLIN_BUILD is enabled, we just trigger the module:compileDebugKotlin task. This avoids executing
  // a few extra tasks that are not required for the preview to refresh.
  if (StudioFlags.COMPOSE_PREVIEW_ONLY_KOTLIN_BUILD.get()) {
    requestKotlinBuild(project, modules)
  }
  else {
    requestCompileJavaBuild(project, modules)
  }
}