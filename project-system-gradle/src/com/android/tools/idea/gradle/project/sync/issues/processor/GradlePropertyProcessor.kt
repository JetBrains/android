/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.projectsystem.toReason
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

class GradlePropertyProcessor(val project: Project, val propertyName: String, val propertyValue: String) : BaseRefactoringProcessor(project) {
  public override fun getCommandName() = "Updating or adding $propertyName gradle property"

  public override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptor {
      override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY
      override fun getProcessedElementsHeader() = "Updating or adding $propertyName gradle property"
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) =
        "References to be updated or added: ${UsageViewBundle.getReferencesString(usagesCount, filesCount)}"
    }
  }

  public override fun findUsages(): Array<UsageInfo> {
    val baseDir = myProject.baseDir ?: return emptyArray()
    val gradlePropertiesVirtualFile = baseDir.findChild("gradle.properties")
    if (gradlePropertiesVirtualFile != null && gradlePropertiesVirtualFile.exists()) {
      val gradlePropertiesPsiFile = PsiManager.getInstance(myProject).findFile(gradlePropertiesVirtualFile)
      if (gradlePropertiesPsiFile is PropertiesFile) {
        val psiElement = when(val property = gradlePropertiesPsiFile.findPropertyByKey(propertyName)) {
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

      val existing = propertiesFile?.findPropertyByKey(propertyName)
      if (existing != null) {
        existing.setValue(propertyValue)
      }
      else {
        propertiesFile?.addProperty(propertyName, propertyValue)
      }
    }

    val projectBuildModel = ProjectBuildModel.get(myProject)
    projectBuildModel.applyChanges()
  }



  public override fun performRefactoring(usages: Array<UsageInfo>) {
    updateProjectBuildModel(usages)

    project.getSyncManager().requestSyncProject(TRIGGER_PROJECT_MODIFIED.toReason())
  }



}