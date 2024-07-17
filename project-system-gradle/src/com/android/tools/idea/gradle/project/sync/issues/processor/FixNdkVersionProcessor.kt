/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_NDK_INSTALLED
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.annotations.VisibleForTesting

/**
 * Tool to rewrite build.gradle file to add or update android.ndkVersion section.
 */
class FixNdkVersionProcessor(
  project: Project,
  private val buildFiles: List<VirtualFile>,
  private val version: String) : BaseRefactoringProcessor(project) {

  /**
   * Produce usage display information.
   */
  public override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptor {
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String {
        return "Values to update " + UsageViewBundle.getReferencesString(usagesCount, filesCount)
      }

      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader(): String {
        return "Update Android NDK Versions"
      }
    }
  }

  /**
   * Find the points in build.gradle where ndkVersion can be updated.
   *
   * There are two cases:
   *
   * (1) There is no pre-existing android.ndkVersion. In this case the PSI element for
   *     the 'android' block is returned.
   *
   * (2) There is a pre-existing android.ndkVersion. In this case the PSI element for
   *     the 'ndkVersion' is returned.
   *
   * Usages are only returned when there is a valid externalNativeBuild block in the
   * build.gradle file.
   */
  public override fun findUsages(): Array<UsageInfo> {
    val projectBuildModel = ProjectBuildModel.get(myProject)

    val usages = ArrayList<UsageInfo>()
    for (file in buildFiles) {
      if (!file.isValid || !file.isWritable) {
        continue
      }
      val android = projectBuildModel.getModuleBuildModel(file).android()

      val externalNativeBuild = android.externalNativeBuild() as GradleDslBlockModel
      if (!externalNativeBuild.hasValidPsiElement()) {
        continue
      }

      val ndkVersion = android.ndkVersion()
      if (version == ndkVersion.toString()) {
        continue
      }

      val element = ndkVersion.fullExpressionPsiElement
      if (element != null) {
        usages.add(UsageInfo(element))
      } else {
        usages.add(UsageInfo((android as GradleDslBlockModel).psiElement!!))
      }
    }
    return usages.toTypedArray()
  }

  /**
   * Performs the actual refactoring on each build.gradle file.
   * Once all build.gradle files have been modified, the project is synced.
   */
  public override fun performRefactoring(usages: Array<UsageInfo>) {
    updateProjectBuildModel()

    GradleSyncInvoker.getInstance().requestProjectSync(myProject, TRIGGER_QF_NDK_INSTALLED)
  }

  @VisibleForTesting
  fun updateProjectBuildModel() {
    val projectBuildModel = ProjectBuildModel.get(myProject)

    for (file in buildFiles) {
      val android = projectBuildModel.getModuleBuildModel(file).android()
      val externalNativeBuild = android.externalNativeBuild() as GradleDslBlockModel
      if (!externalNativeBuild.hasValidPsiElement()) {
        continue
      }
      val ndkVersion = android.ndkVersion()
      ndkVersion.setValue(version)
    }

    projectBuildModel.applyChanges()
  }

  /**
   * Description of this refactoring.
   */
  public override fun getCommandName(): String {
    return "Update Android NDK Version"
  }
}