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
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleProjects
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer

/**
 * Triggers the build of the given [modules] by calling the compile`Variant`Kotlin task
 */
private fun requestKotlinBuild(project: Project, modules: Set<Module>) {
  fun createBuildTasks(module: Module): String? {
    val gradlePath = GradleProjects.getGradleModulePath(module) ?: return null
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
    val request = GradleBuildInvoker.Request(project, path.toFile(), it.value).apply {
      doNotShowBuildOutputOnFailure()
      taskListener = GradleBuildInvoker.getInstance(project).createBuildTaskListener(this, "Build")
    }
    GradleBuildInvoker.getInstance(project).executeTasks(request)
  }
}

/**
 * Triggers the build of the given [modules] by calling the compileSourcesDebug task
 */
private fun requestCompileJavaBuild(project: Project, modules: Set<Module>) =
  GradleBuildInvoker.getInstance(project).compileJava(modules.toTypedArray(), TestCompileType.NONE)

internal fun requestBuild(project: Project, module: Module) {
  if (project.isDisposed || module.isDisposed) {
    return
  }

  if (!GradleProjectInfo.getInstance(project).isBuildWithGradle) {
    // For non gradle projects we just call buildProject instead of trying to invoke a single module build.
    ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()
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

fun hasExistingClassFile(psiFile: PsiFile?) = if (psiFile is PsiClassOwner) {
  val androidModuleSystem by lazy {
    ReadAction.compute<AndroidModuleSystem?, Throwable> {
      psiFile.getModuleSystem()
    }
  }
  ReadAction.compute<List<String>, Throwable> { psiFile.classes.mapNotNull { it.qualifiedName } }
    .mapNotNull { androidModuleSystem?.moduleClassFileFinder?.findClassFile(it) }
    .firstOrNull() != null
}
else false

/**
 * Returns whether the [PsiFile] has been built. It does this by checking the build status of the module if available.
 * If not available, this method will look for the compiled classes and check if they exist.
 *
 * @param project the [Project] the [PsiFile] belongs to.
 * @param lazyFileProvider a lazy provider for the [PsiFile]. It will only be called if needed to obtain the status
 *  of the build.
 */
fun hasBeenBuiltSuccessfully(project: Project, lazyFileProvider: () -> PsiFile): Boolean {
  val result = ProjectSystemService.getInstance(project).projectSystem.getBuildManager().getLastBuildResult()

  if (result.status != ProjectSystemBuildManager.BuildStatus.UNKNOWN) {
    return result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS &&
           result.mode != ProjectSystemBuildManager.BuildMode.CLEAN

  }

  // We do not have information from the last build, try to find if the class file exists
  return hasExistingClassFile(lazyFileProvider())
}

/**
 * Returns whether the [PsiFile] has been built. It does this by checking the build status of the module if available.
 * If not available, this method will look for the compiled classes and check if they exist.
 */
fun hasBeenBuiltSuccessfully(psiFilePointer: SmartPsiElementPointer<PsiFile>): Boolean =
  hasBeenBuiltSuccessfully(psiFilePointer.project) { ReadAction.compute<PsiFile, Throwable> { psiFilePointer.element } }