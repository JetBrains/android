/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.processor

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.plugins.gradle.model.data.BuildParticipant
import org.jetbrains.plugins.gradle.settings.GradleSettings

class UpdateGradlePluginProcessor(
  val project: Project,
  private val pluginToVersionMap: Map<GradlePluginInfo, String>
) : BaseRefactoringProcessor(project) {
  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
    object : UsageViewDescriptor {
      override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY

      override fun getProcessedElementsHeader(): String = "Update Plugin Versions"

      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String =
        "Plugin versions to update (${usagesCount} usage${if (usagesCount == 1) "" else "s"} in $filesCount " +
        "file${if (filesCount == 1) "" else "s"})"
    }

  override fun findUsages(): Array<UsageInfo> {
    return getIncludedBuilds(project).flatMap {
      getUsages(it)
    }.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    val gradlePluginUsages = usages.filterIsInstance<GradlePluginUsageInfo>().filter {
      val element = it.element
      element != null && element.isValid
    }
    gradlePluginUsages.forEach {
      it.dependencyModel.enableSetThrough()
      it.dependencyModel.version().resultModel.setValue(it.newVersion)
    }
    gradlePluginUsages.map { it.projectBuildModel }.distinct().forEach {
      it.applyChanges()
    }
  }

  override fun getCommandName(): String = "Update Plugin Versions"

  private fun getUsages(projectBuildModel: ProjectBuildModel): List<UsageInfo> =
    projectBuildModel.allIncludedBuildModels.flatMap {
      getUsages(projectBuildModel, it)
    }

  private fun getUsages(projectBuildModel: ProjectBuildModel, gradleBuildModel: GradleBuildModel): List<UsageInfo> {
    val artifactDependencies = gradleBuildModel.buildscript().dependencies().artifacts()
    return artifactDependencies.mapNotNull {
      val newVersion = pluginToVersionMap[GradlePluginInfo(it.name().toString(), it.group().toString())]
                       ?: return@mapNotNull null
      val psiElement = it.psiElement ?: return@mapNotNull null
      GradlePluginUsageInfo(projectBuildModel, it, newVersion, psiElement)
    }
  }

  private class GradlePluginUsageInfo(
    val projectBuildModel: ProjectBuildModel,
    val dependencyModel: ArtifactDependencyModel,
    val newVersion: String,
    psiElement: PsiElement
  ) : UsageInfo(psiElement)
}

data class GradlePluginInfo(val name: String, val group: String?)

/**
 * This method returns a list of all participating [ProjectBuildModel] from a given [Project]. This includes any included
 * builds. No ordering is guaranteed.
 *
 *
 * This method should never be called on the UI thread, it will cause the parsing of Gradle build files which can take a long time.
 * The returned [ProjectBuildModel] is not thread safe.
 *
 * @param project the project to obtain all the [ProjectBuildModel]s for
 * @return a list of all [ProjectBuildModel]s
 */
private fun getIncludedBuilds(project: Project): List<ProjectBuildModel> {
  val result = mutableListOf<ProjectBuildModel>()
  result.add(ProjectBuildModel.get(project))
  val basePath = project.basePath ?: return result
  val settings = GradleSettings.getInstance(project).getLinkedProjectSettings(basePath) ?: return result
  val compositeBuild = settings.compositeBuild ?: return result
  result.addAll(compositeBuild
                  .compositeParticipants
                  .mapNotNull { build: BuildParticipant -> build.rootPath }
                  .mapNotNull { ProjectBuildModel.getForCompositeBuild(project, it) })
  return result
}
