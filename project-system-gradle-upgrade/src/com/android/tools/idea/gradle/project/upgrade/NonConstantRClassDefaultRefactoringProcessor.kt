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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
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
import org.jetbrains.android.util.AndroidBundle

class NonConstantRClassDefaultRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val necessityInfo = PointNecessity(AgpVersion.parse("8.0.0-beta01"))

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val baseDir = project.baseDir ?: return usages.toTypedArray()
    val gradlePropertiesVirtualFile = baseDir.findChild("gradle.properties")
    if (gradlePropertiesVirtualFile != null && gradlePropertiesVirtualFile.exists()) {
      val gradlePropertiesPsiFile = PsiManager.getInstance(project).findFile(gradlePropertiesVirtualFile)
      if (gradlePropertiesPsiFile is PropertiesFile) {
        val property = gradlePropertiesPsiFile.findPropertyByKey("android.nonFinalResIds")
        if (property == null) {
          val wrappedPsiElement = WrappedPsiElement(gradlePropertiesPsiFile, this, INSERT_PROPERTY)
          usages.add(NonConstantRClassUsageInfo(wrappedPsiElement))
        }
      }
    }
    else if (baseDir.exists()) {
      val baseDirPsiDirectory = PsiManager.getInstance(project).findDirectory(baseDir)
      if (baseDirPsiDirectory is PsiElement) {
        val wrappedPsiElement = WrappedPsiElement(baseDirPsiDirectory, this, INSERT_PROPERTY)
        usages.add(NonConstantRClassUsageInfo(wrappedPsiElement))
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.NON_CONSTANT_R_CLASS_DEFAULT)

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.nonConstantRClassDefaultRefactoringProcessor.commandName")

  override fun getShortDescription() = """
    R classes in applications and tests previously used constant values that can be
    inlined by the java compiler. The default behaviour in the Android Gradle
    plugin is changing to speed up builds and allow more precise shrinking; this
    processor inserts a property in gradle.properties to preserve the current
    default.
  """.trimIndent()

  override fun getRefactoringId() = "com.android.tools.agp.upgrade.nonConstantRClass"

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("non-constant-r-class-default")

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.nonConstantRClassDefaultRefactoringProcessor.usageView.header")
    }
  }

  companion object {
    val INSERT_PROPERTY = UsageType(AndroidBundle.messagePointer("project.upgrade.nonConstantRClassDefaultRefactoringProcessor.usageType"))
  }
}

class NonConstantRClassUsageInfo(
  private val wrappedElement: WrappedPsiElement
) : GradleBuildModelUsageInfo(wrappedElement) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    val (propertiesFile, psiFile) = when (val realElement = wrappedElement.realElement) {
      is PropertiesFile -> realElement to (realElement as? PsiFile ?: return)
      is PsiDirectory -> (realElement.findFile(FN_GRADLE_PROPERTIES) ?: realElement.createFile (FN_GRADLE_PROPERTIES)).let {
        (it as? PropertiesFile ?: return) to (it as? PsiFile ?: return)
      }
      else -> return
    }
    otherAffectedFiles.add(psiFile)
    propertiesFile.addProperty("android.nonFinalResIds", "false")
  }

  override fun getTooltipText() = AndroidBundle.message("project.upgrade.nonConstantRClassUsageInfo.tooltipText")
}