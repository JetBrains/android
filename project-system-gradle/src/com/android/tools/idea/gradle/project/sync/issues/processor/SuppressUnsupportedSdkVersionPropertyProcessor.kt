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

import com.android.SdkConstants.FN_GRADLE_PROPERTIES
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.annotations.VisibleForTesting

class SuppressUnsupportedSdkVersionPropertyProcessor(
  val project: Project,
  private val sdkVersionsToSuppress: String,
) : BaseRefactoringProcessor(project) {

  public override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptor {
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String {
        return "References to be updated or added: ${UsageViewBundle.getReferencesString(usagesCount, filesCount)}"
      }

      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader(): String {
        return "Updating or adding android.suppressUnsupportedCompileSdk gradle property"
      }
    }
  }

  public override fun findUsages(): Array<UsageInfo> {
    val baseDir = myProject.baseDir ?: return emptyArray()
    val gradlePropertiesVirtualFile = baseDir.findChild("gradle.properties")
    if (gradlePropertiesVirtualFile != null && gradlePropertiesVirtualFile.exists()) {
      val gradlePropertiesPsiFile = PsiManager.getInstance(myProject).findFile(gradlePropertiesVirtualFile)
      if (gradlePropertiesPsiFile is PropertiesFile) {
        val psiElement = when(val property = gradlePropertiesPsiFile.findPropertyByKey("android.suppressUnsupportedCompileSdk")) {
          null -> gradlePropertiesPsiFile
          else -> property.psiElement
        }
        return arrayOf(UsageInfo(psiElement))
      }
    } else if (baseDir.exists()) {
      val baseDirPsiDirectory = PsiManager.getInstance(myProject).findDirectory(baseDir)
      if (baseDirPsiDirectory is PsiElement) {
        return arrayOf(UsageInfo(baseDirPsiDirectory))
      }
    }
    return emptyArray()
  }

  public override fun performRefactoring(usages: Array<UsageInfo>) {
    updateProjectBuildModel(usages)

    GradleSyncInvoker.getInstance().requestProjectSync(myProject, TRIGGER_PROJECT_MODIFIED)
  }

  @VisibleForTesting
  fun updateProjectBuildModel(usages: Array<UsageInfo>) {
    usages.forEach { usage ->
      val propertiesFile = when (val element = usage.element) {
        is PsiFile -> element as? PropertiesFile
        is PsiDirectory -> (element.findFile(FN_GRADLE_PROPERTIES) ?: element.createFile(FN_GRADLE_PROPERTIES)).let {
          (it as? PropertiesFile ?: return@forEach)
        }

        is PsiElement -> element.containingFile as? PropertiesFile
        else -> return@forEach
      }

      val existing = propertiesFile?.findPropertyByKey("android.suppressUnsupportedCompileSdk")
      if (existing != null) {
        existing.setValue(sdkVersionsToSuppress)
      }
      else {
        propertiesFile?.addProperty("android.suppressUnsupportedCompileSdk", sdkVersionsToSuppress)
      }
    }

    val projectBuildModel = ProjectBuildModel.get(myProject)
    projectBuildModel.applyChanges()
  }


  public override fun getCommandName(): String {
    return "Updating or adding android.suppressUnsupportedCompileSdk gradle property"
  }
}