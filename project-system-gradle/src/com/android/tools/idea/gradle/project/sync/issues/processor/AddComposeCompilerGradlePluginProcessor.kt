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
package com.android.tools.idea.gradle.project.sync.issues.processor

import com.android.tools.idea.gradle.dependencies.DependenciesHelper
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_ADD_COMPOSE_COMPILER_GRADLE_PLUGIN
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.annotations.VisibleForTesting

/**
 * Processor to add the Compose Compiler Gradle plugin dependency to the [project] and apply the
 * plugin to the modules that required it (the [affectedModules]).
 */
class AddComposeCompilerGradlePluginProcessor(
  val project: Project,
  val affectedModules: List<Module>,
  val kotlinVersion: String
): BaseRefactoringProcessor(project) {

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor = object : UsageViewDescriptor {
    override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY

    override fun getProcessedElementsHeader(): String = "Apply the Compose Compiler Gradle plugin"

    override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String {
      val fileOrFiles = if (filesCount == 1) "file" else "files"
      return "Build $fileOrFiles to apply the Compose Compiler Gradle plugin to ($filesCount $fileOrFiles found)"
    }
  }

  public override fun findUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    val projectBuildModel = ProjectBuildModel.get(myProject)
    for (module in affectedModules) {
      projectBuildModel.getModuleBuildModel(module)?.psiFile?.let {usages.add(UsageInfo(it)) }
    }
    return usages.toTypedArray()
  }

  public override fun performRefactoring(usages: Array<out UsageInfo>) {
    updateProjectBuildModel()

    GradleSyncInvoker.getInstance().requestProjectSync(myProject, TRIGGER_QF_ADD_COMPOSE_COMPILER_GRADLE_PLUGIN)
  }

  @VisibleForTesting
  fun updateProjectBuildModel() {
    val projectBuildModel = ProjectBuildModel.get(myProject)
    val moduleBuildModels = affectedModules.mapNotNull { projectBuildModel.getModuleBuildModel(it) }
    DependenciesHelper.withModel(projectBuildModel)
      .addPlugin(
        "org.jetbrains.kotlin.plugin.compose",
        "org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion",
        moduleBuildModels
      )

    projectBuildModel.applyChanges()
  }

  public override fun getCommandName() = "Add Compose Compiler Gradle plugin"
}
