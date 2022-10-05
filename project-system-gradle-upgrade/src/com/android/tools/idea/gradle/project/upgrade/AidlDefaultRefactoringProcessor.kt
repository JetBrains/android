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

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
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
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.android.util.AndroidBundle

class AidlDefaultRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = AgpUpgradeComponentNecessity.standardPointNecessity(current, new, AgpVersion.parse("8.0.0-alpha04"))

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val explicitProperty =
      projectBuildModel.projectBuildModel?.propertiesModel?.declaredProperties
        ?.any { it.name == "android.defaults.buildfeatures.aidl" }
      ?: false
    if (explicitProperty) return UsageInfo.EMPTY_ARRAY
    val modules = ModuleManager.getInstance(project).modules.filter { it.isMainModule() }
    modules.forEach module@{ module ->
      val facet = module.androidFacet ?: return@module
      // Note: This only finds the aidl directories for the main artifact.  This is probably OK.
      val aidlDirectories = SourceProviderManager.getInstance(facet).sources.aidlDirectories
      // Note: This test is conservative in that any content (even e.g. empty subdirectories) under the aidl
      // roots will cause this processor to be active.
      val isAidlUsed = aidlDirectories.any { it.exists() && it.children.isNotEmpty() }
      if (!isAidlUsed) return@module
      val buildModel = projectBuildModel.getModuleBuildModel(module) ?: return@module
      val aidlModel = buildModel.android().buildFeatures().aidl()
      val aidlEnabled = aidlModel.getValue(BOOLEAN_TYPE)
      if (aidlEnabled != null) return@module

      val buildFeaturesOrHigherPsiElement =
        listOf(buildModel.android().buildFeatures(), buildModel.android(), buildModel)
          .firstNotNullOfOrNull { it.psiElement }
        ?: return@module
      val psiElement = WrappedPsiElement(buildFeaturesOrHigherPsiElement, this, INSERT_AIDL_DIRECTIVE)
      usages.add(RenderScriptEnableUsageInfo(psiElement, aidlModel))
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.AIDL_DEFAULT)

  override fun getCommandName() = AndroidBundle.message("project.upgrade.aidlDefaultRefactoringProcessor.commandName")

  override fun getShortDescription() = """
    The default value for buildFeatures.aidl has changed, and some modules
    in this project appear to be using AIDL.  Build files will be modified
    to explicitly enable AIDL in modules with AIDL sources.
  """.trimIndent()

  override fun getRefactoringId() = "com.android.tools.agp.upgrade.aidlDefault"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.aidlDefaultRefactoringProcessor.usageView.header")
    }
  }

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("aidl-default")

  companion object {
    val INSERT_AIDL_DIRECTIVE =
      UsageType(AndroidBundle.messagePointer("project.upgrade.aidlDefaultRefactoringProcessor.enable.usageType"))
  }
}

class AidlEnableUsageInfo(
  element: WrappedPsiElement,
  private val resultModel: GradlePropertyModel,
): GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.aidlBuildFeature.enable.tooltipText")

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    resultModel.setValue(true)
  }
}