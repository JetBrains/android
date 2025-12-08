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
import com.android.tools.idea.gradle.dsl.model.android.android
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.OBJECT_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.IRRELEVANT_PAST
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType

class DynamicFeatureConsumerProguardFilesProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val necessityInfo = object : AgpUpgradeComponentNecessityInfo() {
    override fun computeNecessity(current: AgpVersion, new: AgpVersion) = when {
      new < AgpVersion.parse("7.0.0-alpha13") -> OPTIONAL_INDEPENDENT
      current > AgpVersion.parse("9.0.0-beta01") -> IRRELEVANT_PAST
      else -> MANDATORY_INDEPENDENT
    }
  }

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      val modelPsiElement = model.psiElement ?: return@model
      if (model.moduleKind != ModuleKind.DYNAMIC_FEATURE) return@model
      model.computePropertyList().forEach { (from, to) ->
        val psiElement = from.representativeContainedPsiElement ?: return@forEach
        val wrappedPsiElement = WrappedPsiElement(psiElement, this, TRANSFER_ENTRIES_USAGE_TYPE)
        val usageInfo = TransferEntriesUsageInfo(wrappedPsiElement, from, to)
        usages.add(usageInfo)
      }
    }
    return usages.toTypedArray()
  }

  override fun getCommandName(): String = AgpUpgradeBundle.message("dynamicFeatureConsumerProguardFiles.commandName")

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.DYNAMIC_FEATURE_CONSUMER_PROGUARD_FILES)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo?>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AgpUpgradeBundle.message("dynamicFeatureConsumerProguardFiles.usageView.header")
    }
  }

  fun GradleBuildModel.computePropertyList() =
    listOf(android().defaultConfig().let { it.consumerProguardFiles() to it.proguardFiles() }) +
      android().buildTypes().map { it.consumerProguardFiles() to it.proguardFiles() } +
      android().productFlavors().map { it.consumerProguardFiles() to it.proguardFiles() }

  companion object {
    val TRANSFER_ENTRIES_USAGE_TYPE = UsageType(AgpUpgradeBundle.messagePointer("dynamicFeatureConsumerProguardFiles.usageType"))
  }
}

class TransferEntriesUsageInfo(
  element: WrappedPsiElement,
  val from: ResolvedPropertyModel,
  val to: ResolvedPropertyModel
): GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    from.getValue(LIST_TYPE)?.forEach {
      val value = it.getValue(OBJECT_TYPE) ?: return@forEach
      to.addListValue()?.setValue(value)
    }
    from.delete()
  }

  override fun getTooltipText(): String = AgpUpgradeBundle.message("dynamicFeatureConsumerProguardFiles.tooltipText")
}
