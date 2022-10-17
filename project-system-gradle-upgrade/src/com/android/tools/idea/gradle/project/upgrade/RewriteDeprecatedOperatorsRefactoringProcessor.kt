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

import com.android.ide.common.repository.AgpVersion
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

val REWRITE_DEPRECATED_OPERATORS = PropertiesOperationsRefactoringInfo(
  optionalFromVersion = AgpVersion.parse("7.1.0-alpha06"),
  requiredFromVersion = AgpVersion.parse("9.0.0-alpha01"),
  commandNameSupplier = AndroidBundle.messagePointer("project.upgrade.rewriteDeprecatedOperatorsRefactoringProcessor.commandName"),
  shortDescriptionSupplier = {
    """
      A number of Dsl operators and methods have been deprecated for removal in
      AGP 9.0.0, replaced by simpler property operations.
    """.trimIndent()
  },
  processedElementsHeaderSupplier = AndroidBundle.messagePointer("project.upgrade.rewriteDeprecatedOperatorsRefactoringProcessor.usageView.header"),
  componentKind = UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.REWRITE_DEPRECATED_OPERATORS,
  propertiesOperationInfos = listOf(
    RewriteObsoletePropertiesInfo(
      propertyModelListGetter = {
        listOf(
          android().buildToolsVersion(),
          // TODO(b/205806471) android().compileSdkVersion(),
          android().flavorDimensions()) +
        android().defaultConfig().let {
          listOf(
            it.manifestPlaceholders(),
            it.matchingFallbacks(),
            // TODO(b/205806471) it.maxSdkVersion(),
            // TODO(b/205806471) it.minSdkVersion(),
            it.resConfigs(),
            // TODO(b/205806471) it.targetSdkVersion(),
            it.testFunctionalTest(),
            it.testHandleProfiling(),
            it.testInstrumentationRunnerArguments(),
          )
        } +
        android().buildTypes().flatMap {
          listOf(
            it.manifestPlaceholders(),
            it.matchingFallbacks(),
          )
        } +
        android().productFlavors().flatMap {
          listOf(
            it.dimension(),
            it.manifestPlaceholders(),
            it.matchingFallbacks(),
            // TODO(b/205806471) it.maxSdkVersion(),
            // TODO(b/205806471) it.minSdkVersion(),
            it.resConfigs(),
            // TODO(b/205806471) it.targetSdkVersion(),
            it.testFunctionalTest(),
            it.testHandleProfiling(),
            it.testInstrumentationRunnerArguments(),
          )
        }
      },
      tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.deprecatedOperatorUsageInfo.rewrite.tooltipText"),
      usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.rewriteDeprecatedOperatorsRefactoringProcessor.rewrite.usageType")),
    )
  )
)
