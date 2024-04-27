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
package com.android.tools.idea.actions.annotations

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.util.addDependenciesWithUiConfirmation
import com.android.tools.idea.util.dependsOn
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.usageView.UsageInfo
import java.nio.file.FileSystems

class InferAnnotationsGradleToken : InferAnnotationsToken<GradleProjectSystem>, GradleToken {
  override fun checkDependencies(project: Project, usageInfos: Array<UsageInfo>, runnable: Runnable): Boolean {
    val modules = findModulesFromUsage(usageInfos)
    return checkModules(project, modules, runnable)
  }

  private fun findModulesFromUsage(infos: Array<UsageInfo>): Map<Module, PsiFile> {
    // We need 1 file from each module that requires changes (the file may be overwritten below):
    val modules: MutableMap<Module, PsiFile> = HashMap()
    for (info in infos) {
      val element = info.element ?: continue
      val module = ModuleUtilCore.findModuleForPsiElement(element) ?: continue
      val file = element.containingFile
      modules[module] = file
    }
    return modules
  }

  // See whether we need to add the androidx annotations library to the dependency graph
  private fun checkModules(
    project: Project,
    modules: Map<Module, PsiFile>,
    runnable: Runnable
  ): Boolean {
    val artifact = getAnnotationsMavenArtifact(project)
    val modulesWithoutAnnotations = modules.keys.filter { module -> !module.dependsOn(artifact) }.toSet()
    if (modulesWithoutAnnotations.isEmpty()) {
      return true
    }
    val moduleNames = StringUtil.join(modulesWithoutAnnotations, { obj: Module -> obj.name }, ", ")
    val count = modulesWithoutAnnotations.size
    val message = String.format(
      """
      The %1${"$"}s %2${"$"}s %3${"$"}sn't refer to the existing '%4${"$"}s' library with Android annotations.

      Would you like to add the %5${"$"}s now?
      """.trimIndent(),
      StringUtil.pluralize("module", count),
      moduleNames,
      if (count > 1) "do" else "does",
      GoogleMavenArtifactId.SUPPORT_ANNOTATIONS.mavenArtifactId,
      StringUtil.pluralize("dependency", count)
    )
    if (Messages.showOkCancelDialog(
        project,
        message,
        "Infer Annotations",
        "OK",
        "Cancel",
        Messages.getErrorIcon()
      ) == Messages.OK
    ) {
      val manager = RepositoryUrlManager.get()
      val revision = manager.getLibraryRevision(artifact.mavenGroupId, artifact.mavenArtifactId, null, false, FileSystems.getDefault())
      if (revision != null) {
        val coordinates = listOf(artifact.getCoordinate(revision))
        for (module in modulesWithoutAnnotations) {
          val added = module.addDependenciesWithUiConfirmation(coordinates, false, requestSync = false)
          if (added.isEmpty()) {
            break // user canceled or some other problem; don't resume with other modules
          }
        }
        runnable.run()
      }
    }
    return false
  }

  // In theory, we should look up project.isAndroidX() here and pick either SUPPORT_ANNOTATIONS or ANDROIDX_SUPPORT_ANNOTATIONS
  // but calling project.isAndroidX is getting caught in the SlowOperations check in recent IntelliJs. And androidx is a reasonable
  // requirement now; support annotations are dying out.
  private fun getAnnotationsMavenArtifact(project: Project) = GoogleMavenArtifactId.ANDROIDX_SUPPORT_ANNOTATIONS
}