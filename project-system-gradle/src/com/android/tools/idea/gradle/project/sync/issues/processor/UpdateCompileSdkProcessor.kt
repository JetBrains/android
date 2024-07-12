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
package com.android.tools.idea.gradle.project.sync.issues.processor

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_MIN_COMPILE_SDK_UPDATED
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.toArray
import java.util.Arrays
import java.util.stream.Collectors

class UpdateCompileSdkProcessor(
  val project: Project,
  private val buildFilesWithNewMinCompileSdk: Map<VirtualFile, Int>): BaseRefactoringProcessor(project) {
  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
    object : UsageViewDescriptor {
      override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY

      override fun getProcessedElementsHeader(): String = "Update Compile SDK Versions"

      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String =
        "Compile Sdk versions to update (${usagesCount} usage${if (usagesCount == 1) "" else "s"} in $filesCount " +
        "file${if (filesCount == 1) "" else "s"})"
    }

  @VisibleForTesting
  public override fun findUsages(): Array<UsageInfo> {
    val projectBuildModel = ProjectBuildModel.get(myProject)

    val usages: MutableList<UsageInfo> = ArrayList()
    for ((file, newMinCompileSdk) in buildFilesWithNewMinCompileSdk) {

      if (!file.isValid || !file.isWritable) {
        continue
      }
      val android = projectBuildModel.getModuleBuildModel(file).android()
      val existingCompileSdkVersion = android.compileSdkVersion()
      if (newMinCompileSdk.toString() == existingCompileSdkVersion.toString()) {
        continue
      }
      val element = existingCompileSdkVersion.fullExpressionPsiElement
      if (element != null) {
        usages.add(UsageInfo(element))
      }
    }
    return usages.toArray(UsageInfo.EMPTY_ARRAY)
  }

  @VisibleForTesting
  public override fun performRefactoring(usages: Array<out UsageInfo>) {
    updateProjectBuildModel(usages)

    GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_MIN_COMPILE_SDK_UPDATED)
  }

  @VisibleForTesting
  fun updateProjectBuildModel(usages: Array<out UsageInfo>) {
    val projectBuildModel = ProjectBuildModel.get(myProject)

    val elements = Arrays.stream(usages).map { usage: UsageInfo -> usage.element }.collect(Collectors.toList())
    for ((file, newMinCompileSdk) in buildFilesWithNewMinCompileSdk) {
      val android = projectBuildModel.getModuleBuildModel(file).android()
      val compileSdkVersion = android.compileSdkVersion()
      val element = compileSdkVersion.fullExpressionPsiElement
      if (element != null && elements.contains(element)) {
        compileSdkVersion.setValue(newMinCompileSdk)
      }
    }
    projectBuildModel.applyChanges()
  }

  override fun getCommandName(): String = "Update Compile Sdk Version"
}