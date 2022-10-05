/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.dsl.api.util.DeletablePsiElementHolder
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

class RedundantPropertiesRefactoringProcessor: AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity(): AgpUpgradeComponentNecessity = MANDATORY_CODEPENDENT

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      val modelPsiElement = model.psiElement ?: return@model
      val buildToolsVersionModel = model.android().buildToolsVersion()
      val buildToolsVersion = buildToolsVersionModel.valueAsString()
                                ?.let { GradleVersion.tryParse(it) }
                                ?.takeIf { it > GradleVersion.parse("0.0.0") } ?: return@model
      if (buildToolsVersion < minimumBuildToolsVersion(new)) {
        val psiElement = buildToolsVersionModel.representativeContainedPsiElement ?: return@model
        val wrappedPsiElement = WrappedPsiElement(psiElement, this, REDUNDANT_PROPERTY_USAGE_TYPE)
        val usageInfo = RemoveRedundantPropertyUsageInfo(wrappedPsiElement, buildToolsVersionModel)
        usages.add(usageInfo)
      }
    }
    return usages.toTypedArray()
  }

  fun minimumBuildToolsVersion(agpVersion: AgpVersion): GradleVersion = when {
    // When upgrading to versions earlier than 7.0.0-alpha01, don't remove buildToolsVersion.  (Make that happen by returning a
    // version that will be earlier than the user's specified buildToolsVersion.
    // TODO(xof): extend this table both to the future and the past, or replace it with a more automated mechanism.
    AgpVersion.parse("7.0.0-alpha01") > agpVersion -> GradleVersion.parse("0.0.1")
    AgpVersion.parse("7.1.0-alpha01") > agpVersion -> GradleVersion.parse("30.0.2")
    else -> GradleVersion.parse("30.0.3")
  }

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.redundantPropertiesRefactoringProcessor.commandName")

  override fun getShortDescription(): String =
    """
      Setting buildToolsVersion to a value that is lower than the minimum supported by the
      Android Gradle Plugin has no effect (other than a warning).  The setting can be
      safely removed.
    """.trimIndent()

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.REDUNDANT_PROPERTIES)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.redundantPropertiesRefactoringProcessor.usageView.header")
    }
  }

  companion object {
    val REDUNDANT_PROPERTY_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.redundantProperties.usageType"))
  }
}

class RemoveRedundantPropertyUsageInfo(
  element: WrappedPsiElement,
  val model: DeletablePsiElementHolder
): GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.delete()
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.redundantProperties.tooltipText")
}
