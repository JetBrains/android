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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.project.getPackageName
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

class BuildConfigDefaultRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override var necessityInfo = PointNecessity(AgpVersion.parse("8.0.0-alpha09"))

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val baseDir = project.baseDir ?: return usages.toTypedArray()
    val gradlePropertiesVirtualFile = baseDir.findChild("gradle.properties")
    if (gradlePropertiesVirtualFile != null && gradlePropertiesVirtualFile.exists()) {
      val gradlePropertiesPsiFile = PsiManager.getInstance(project).findFile(gradlePropertiesVirtualFile)
      if (gradlePropertiesPsiFile is PropertiesFile) {
        val property = gradlePropertiesPsiFile.findPropertyByKey("android.defaults.buildfeatures.buildconfig")
        if (property == null) {
          val wrappedPsiElement = WrappedPsiElement(gradlePropertiesPsiFile, this, INSERT_PROPERTY)
          usages.add(BuildConfigUsageInfo(wrappedPsiElement))
        }
      }
    }
    else if (baseDir.exists()) {
      val baseDirPsiDirectory = PsiManager.getInstance(project).findDirectory(baseDir)
      if (baseDirPsiDirectory is PsiElement) {
        val wrappedPsiElement = WrappedPsiElement(baseDirPsiDirectory, this, INSERT_PROPERTY)
        usages.add(BuildConfigUsageInfo(wrappedPsiElement))
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.BUILD_CONFIG_DEFAULT)

  override fun getCommandName() = AndroidBundle.message("project.upgrade.buildConfigDefaultRefactoringProcessor.commandName")

  override fun getShortDescription() = """
    The default value for buildFeatures.buildConfig is changing, meaning that
    the Android Gradle Plugin will no longer generate BuildConfig classes by default.
    This processor adds a directive to preserve the previous behavior of generating
    BuildConfig classes for all modules; if this project does not use BuildConfig, you
    can remove the android.defaults.buildfeatures.buildconfig property from the
    project's gradle.properties file after this upgrade.
  """.trimIndent()

  override fun getRefactoringId() = "com.android.tools.agp.upgrade.buildConfigDefault"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.buildConfigDefaultRefactoringProcessor.usageView.header")
    }
  }

  // TODO(xof): find target for redirect
  override val readMoreUrlRedirect = ReadMoreUrlRedirect("build-config-default")

  companion object {
    val INSERT_PROPERTY = UsageType(AndroidBundle.messagePointer("project.upgrade.buildConfigDefaultRefactoringProcessor.enable.usageType"))
  }
}

class BuildConfigUsageInfo(private val wrappedElement: WrappedPsiElement): GradleBuildModelUsageInfo(wrappedElement) {
  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.buildConfigBuildFeature.enable.tooltipText")

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    val (propertiesFile, psiFile) = when (val realElement = wrappedElement.realElement) {
      is PropertiesFile -> realElement to (realElement as? PsiFile ?: return)
      is PsiDirectory -> (realElement.findFile(FN_GRADLE_PROPERTIES) ?: realElement.createFile (FN_GRADLE_PROPERTIES)).let {
        (it as? PropertiesFile ?: return) to (it as? PsiFile ?: return)
      }
      else -> return
    }
    otherAffectedFiles.add(psiFile)
    propertiesFile.addProperty("android.defaults.buildfeatures.buildconfig", "true")
  }
}