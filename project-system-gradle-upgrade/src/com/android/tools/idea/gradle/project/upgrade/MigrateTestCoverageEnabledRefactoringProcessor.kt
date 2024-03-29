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
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.usageView.UsageInfo
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

val MIGRATE_TEST_COVERAGE_ENABLED_TO_UNIT_AND_ANDROID_COVERAGE =
  PropertiesOperationsRefactoringInfo(
    optionalFromVersion = AgpVersion.parse("7.3.0"),
    requiredFromVersion = AgpVersion.parse("9.0.0-alpha01"),
    commandNameSupplier = AndroidBundle.messagePointer("project.upgrade.migrateTestCoverageEnabledRefactoringProcessor.commandName"),
    shortDescriptionSupplier = { """
      Starting with Android Gradle Plugin 7.3.0 testCoverageEnabled has
      been replaced by enableUnitTestCoverage and enableAndroidTestCoverage.
      It will be removed in AGP 9.0.0.
    """.trimIndent()
    },
    processedElementsHeaderSupplier = AndroidBundle.messagePointer("project.upgrade.migrateTestCoverageEnabledRefactoringProcessor.usageView.header"),
    componentKind = UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.MIGRATE_TEST_COVERAGE_ENABLED,
    propertiesOperationInfos = listOf(
      MoveTestCoveragePropertiesInfo(),
    ),
  )

private class MoveTestCoveragePropertiesInfo: PropertiesOperationInfo{
  val usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateTestCoverageEnabledRefactoringProcessor.move.usageType"))
  val tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.migrateTestCoverageEnabledRefactoringProcessor.move.tooltipText")

  override fun findBuildModelUsages(
    processor: AgpUpgradeComponentRefactoringProcessor,
    buildModel: GradleBuildModel
  ): ArrayList<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    buildModel.android().buildTypes().forEach { buildType ->
      val testCoverageEnabled = buildType.testCoverageEnabled()
      if (testCoverageEnabled.getValue(GradlePropertyModel.OBJECT_TYPE) != null) {
        val psiElement = testCoverageEnabled.psiElement ?: return@forEach
        val wrappedPsiElement = WrappedPsiElement(psiElement, processor, usageType)
        val usageInfo = MovePropertyUsageInfo(wrappedPsiElement, buildType, testCoverageEnabled)
        usages.add(usageInfo)
      }
    }
    return usages
  }

  inner class MovePropertyUsageInfo(
    element: WrappedPsiElement,
    val buildType: BuildTypeModel,
    val testCoverageEnabled: ResolvedPropertyModel,
  ) : GradleBuildModelUsageInfo(element) {
    override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
      val valueModel = testCoverageEnabled.unresolvedModel

      val value: Any = when (valueModel.valueType) {
        GradlePropertyModel.ValueType.LIST -> valueModel.getValue(GradlePropertyModel.LIST_TYPE) ?: return
        GradlePropertyModel.ValueType.REFERENCE -> valueModel.getValue(GradlePropertyModel.REFERENCE_TO_TYPE) ?: return
        else -> valueModel.getValue(GradlePropertyModel.OBJECT_TYPE) ?: return
      }

      buildType.enableUnitTestCoverage().setValue(value)
      buildType.enableAndroidTestCoverage().setValue(value)
      testCoverageEnabled.delete()
    }

    override fun getTooltipText(): String = tooltipTextSupplier.get()

    override fun getDiscriminatingValues(): List<Any> = listOf(this@MoveTestCoveragePropertiesInfo)
  }
}