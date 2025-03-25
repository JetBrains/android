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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.openapi.project.guessProjectDir
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType

/**
 * Refactor that looks for a gradle property in which the default value changes. This refactoring is applied only if the property is not
 * defined and the update is done between two versions in which the default value is different
 */
abstract class AbstractBooleanPropertyDefaultRefactoringProcessor: AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  abstract val propertyKey: String
  abstract val oldDefault: Boolean
  abstract val upgradeEventKind: UpgradeAssistantComponentKind
  abstract val insertPropertyText: String
  abstract val tooltip: String
  abstract val usageViewHeader: String

  // Make it abstract again to force subclasses to define their own id
  abstract override fun getRefactoringId(): String


  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val baseDir = project.guessProjectDir() ?: return usages.toTypedArray()
    val gradlePropertiesVirtualFile = baseDir.findChild("gradle.properties")
    if (gradlePropertiesVirtualFile != null && gradlePropertiesVirtualFile.exists()) {
      val gradlePropertiesPsiFile = PsiManager.getInstance(project).findFile(gradlePropertiesVirtualFile)
      if (gradlePropertiesPsiFile is PropertiesFile) {
        val property = gradlePropertiesPsiFile.findPropertyByKey(propertyKey)
        if (property == null) {
          val wrappedPsiElement = WrappedPsiElement(gradlePropertiesPsiFile, this, usageType())
          usages.add(GradlePropertyUsageInfo(wrappedPsiElement, propertyKey, oldDefault, tooltip))
        }
      }
    }
    else if (baseDir.exists()) {
      val baseDirPsiDirectory = PsiManager.getInstance(project).findDirectory(baseDir)
      if (baseDirPsiDirectory is PsiElement) {
        val wrappedPsiElement = WrappedPsiElement(baseDirPsiDirectory, this, usageType())
        usages.add(GradlePropertyUsageInfo(wrappedPsiElement, propertyKey, oldDefault, tooltip))
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(upgradeEventKind)

  fun usageType() = UsageType(insertPropertyText)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = usageViewHeader
    }
  }
}

class GradlePropertyUsageInfo(private val wrappedElement: WrappedPsiElement, val key: String, val oldDefault: Boolean, val tooltip: String): GradleBuildModelUsageInfo(wrappedElement) {
  override fun getTooltipText(): String = tooltip

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    val propertiesFile = when (val realElement = wrappedElement.realElement) {
      is PropertiesFile -> realElement
      is PsiDirectory -> (realElement.findFile(FN_GRADLE_PROPERTIES) ?: realElement.createFile (FN_GRADLE_PROPERTIES)) as? PropertiesFile ?: return
      else -> return
    }
    val psiFile = propertiesFile as? PsiFile ?: return
    otherAffectedFiles.add(psiFile)
    propertiesFile.addProperty(key, oldDefault.toString())
  }
}