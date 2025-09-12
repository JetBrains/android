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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

/**
 * Starting with AGP 9.0.0, the previously deprecated `targetSdkVersion` property in the
 * `defaultConfig` block of library modules will be removed. This processor finds all occurrences
 * of `android.defaultConfig.targetSdkVersion` and migrates the value to `android.lint.targetSdkVersion`
 * and `android.testOptions.targetSdkVersion`, deleting the original property.
 */
class DeprecatedLibraryTargetSdkRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val necessityInfo = PointNecessity(AgpVersion.parse("9.0.0-alpha01"))

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = projectBuildModel.allIncludedBuildModels.mapNotNull { model ->
      val plugins = model.appliedPlugins()
      if (plugins.find { it.name().toString() == "com.android.library" } == null) {
        return@mapNotNull null
      }

      val targetSdkProperty = model.android().defaultConfig().targetSdkVersion()
      val targetSdkPsiElement = targetSdkProperty.psiElement ?: return@mapNotNull null
      val wrappedPsiElement = WrappedPsiElement(targetSdkPsiElement, this, REMOVE_DEPRECATED_PROPERTY_USAGE_TYPE )

      val sdkVersionValue = targetSdkProperty.toInt() ?: return@mapNotNull null
      RefactoringUsageInfo(wrappedPsiElement, model, sdkVersionValue)
    }.toTypedArray()

    return usages
  }

  override fun getCommandName() = AndroidBundle.message("project.upgrade.deprecatedLibraryTargetSdk.commandName")!!

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.REMOVE_DEPRECATED_LIBRARY_TARGET_SDK)

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor = object : UsageViewDescriptorAdapter() {
    override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY
    override fun getProcessedElementsHeader() =
      AndroidBundle.message("project.upgrade.deprecatedLibraryTargetSdk.commandName")
  }

  companion object {
    val REMOVE_DEPRECATED_PROPERTY_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.deprecatedLibraryTargetSdk.usageType"))
  }

  class RefactoringUsageInfo(
    element: WrappedPsiElement,
    val buildModel: GradleBuildModel,
    val sdkVersion: Int

  ) : GradleBuildModelUsageInfo(element) {
    override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.deprecatedLibraryTargetSdk.tooltipText")!!

    override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
      val androidModel = buildModel.android()

      androidModel.defaultConfig().targetSdkVersion().delete()
      androidModel.lint().targetSdkVersion().setValue(sdkVersion)
      androidModel.testOptions().targetSdkVersion().setValue(sdkVersion)
    }
  }
}