/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.model.repositories.JCenterRepositoryModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_REMOVE_JCENTER_FROM_REPOSITORIES
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.annotations.VisibleForTesting

/**
 * Processor to remove jcenter usages in the following places:
 *   - Project's build.gradle buildScript.repositories block
 *   - Project's settings.gradle dependencyResolutionManagement.repositories block
 *   - Affected modules build.gradle repositories block
 */
class RemoveJcenterProcessor(val project: Project, val affectedModules: List<Module>): BaseRefactoringProcessor(project) {
  public override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptor {
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String {
        return "References to be removed: ${UsageViewBundle.getReferencesString(usagesCount, filesCount)}"
      }

      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Remove JCenter from repositories."
    }
  }

  public override fun findUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()

    // Check project's build.gradle
    val projectBuildModel = ProjectBuildModel.get(myProject)
    val buildModel = projectBuildModel.projectBuildModel
    if (buildModel != null) {
      usages.addAll(usagesFromRepositories(buildModel.buildscript().repositories()))
    }

    // Project's settings.gradle
    val settingsModel = projectBuildModel.projectSettingsModel
    if (settingsModel != null) {
      usages.addAll(usagesFromRepositories(settingsModel.dependencyResolutionManagement().repositories()))
    }

    // Modules' build.gradle
    for (module in affectedModules) {
      val moduleModel = projectBuildModel.getModuleBuildModel(module) ?: continue
      usages.addAll(usagesFromRepositories(moduleModel.repositories()))
    }
    return usages.toTypedArray()
  }

  private fun usagesFromRepositories(repositories: RepositoriesModel): Collection<UsageInfo> {
    val usages: ArrayList<UsageInfo> = ArrayList()
    val jcenterModels = extractJcenterModel(repositories)
    for (model in jcenterModels) {
      if (model.psiElement == null)
        continue
      usages.add(RepositoryUsageInfo(model))
    }
    return usages
  }

  class RepositoryUsageInfo(val model: JCenterRepositoryModel) : UsageInfo(model.psiElement!!) {
    fun removeUsage() {
      model.dslElement.delete()
    }
  }

  private fun extractJcenterModel(repositories: RepositoriesModel): List<JCenterRepositoryModel> {
    return repositories.repositories().filterIsInstance(JCenterRepositoryModel::class.java)
  }

  public override fun performRefactoring(usages: Array<out UsageInfo>) {
    updateProjectBuildModel(usages)

    GradleSyncInvoker.getInstance().requestProjectSync(myProject, TRIGGER_QF_REMOVE_JCENTER_FROM_REPOSITORIES)
  }

  @VisibleForTesting
  fun updateProjectBuildModel(usages: Array<out UsageInfo>) {
    val projectBuildModel = ProjectBuildModel.get(myProject)
    for (usage in usages) {
      if (usage is RepositoryUsageInfo) {
        usage.removeUsage()
      }
    }
    projectBuildModel.applyChanges()
  }

  public override fun getCommandName() = "Remove JCenter From Repositories"
}
