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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

/**
 * Staring AGP 9.0 android.defaults.buildFeatures.shaders will be set to false by default, causing projects that have shaders to not use AOT
 * compile unless the project explicitly says so. This refactoring is intended to let users know that and offer to set
 * android.defaults.buildFeatures.shaders to true if the upgrade assistant detects that the project has shaders folders.
 */
class ShadersDefaultRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)

  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val necessityInfo = RegionNecessity(AgpVersion.parse("8.4.0-alpha10"), AgpVersion.parse("9.0.0-alpha01"))

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val explicitProperty =
      projectBuildModel.projectBuildModel?.propertiesModel?.declaredProperties
        ?.any { it.name == "android.defaults.buildfeatures.shaders" }
      ?: false
    if (explicitProperty) return UsageInfo.EMPTY_ARRAY
    val modules = ModuleManager.getInstance(project).modules.filter { it.isMainModule() }
    modules.forEach module@{ module ->
      // Check if the property is set explicitly, if it is we should not modify it
      val buildModel = projectBuildModel.getModuleBuildModel(module) ?: return@module
      val shadersModel = buildModel.android().buildFeatures().shaders()
      val shadersEnabled = shadersModel.getValue(GradlePropertyModel.BOOLEAN_TYPE)
      if (shadersEnabled != null) return@module

      val facet = module.androidFacet ?: return@module
      // Note: This only finds the shaders directories for the main artifact.  This is probably OK.
      val shadersDirectories = SourceProviderManager.getInstance(facet).sources.shadersDirectories
      // Note: This test is conservative in that any content (even e.g. empty subdirectories) under the shaders
      // roots will cause this processor to be active.
      val isShadersUsed = shadersDirectories.any { it.exists() && it.children.isNotEmpty() }
      if (!isShadersUsed) return@module

      val buildFeaturesOrHigherPsiElement =
        listOf(buildModel.android().buildFeatures(), buildModel.android(), buildModel)
          .firstNotNullOfOrNull { it.psiElement }
        ?: return@module
      val psiElement = WrappedPsiElement(buildFeaturesOrHigherPsiElement, this, INSERT_SHADERS_DIRECTIVE)
      usages.add(ShadersEnableUsageInfo(psiElement, shadersModel))
    }
    return usages.toTypedArray()
  }

  override fun getCommandName() = AndroidBundle.message("project.upgrade.shadersDefaultRefactoringProcessor.commandName")

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.SHADERS_DEFAULT)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.shadersDefaultRefactoringProcessor.usageView.header")
    }
  }

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("shaders-default")

  companion object {
    val INSERT_SHADERS_DIRECTIVE =
      UsageType(AndroidBundle.messagePointer("project.upgrade.shadersDefaultRefactoringProcessor.enable.usageType"))
  }
}

class ShadersEnableUsageInfo(
  element: WrappedPsiElement,
  private val resultModel: GradlePropertyModel,
): GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.shadersBuildFeature.enable.tooltipText")

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    resultModel.setValue(true)
  }
}
