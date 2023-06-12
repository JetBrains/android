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
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

val MIGRATE_JACOCO_TO_TEST_COVERAGE =
  PropertiesOperationsRefactoringInfo(
    optionalFromVersion = AgpVersion.parse("7.0.1"),
    requiredFromVersion = AgpVersion.parse("9.0.0-alpha01"),
    commandNameSupplier = AndroidBundle.messagePointer("project.upgrade.migrateToTestCoverageRefactoringProcessor.commandName"),
    shortDescriptionSupplier = { """
      Configuration related to test coverage is now performed using
      the testCoverage block.
    """.trimIndent()
    },
    processedElementsHeaderSupplier = AndroidBundle.messagePointer("project.upgrade.migrateToTestCoverageRefactoringProcessor.usageView.header"),
    componentKind = UpgradeAssistantComponentKind.MIGRATE_TO_TEST_COVERAGE,
    propertiesOperationInfos = listOf(
      MovePropertiesInfo(
        sourceToDestinationPropertyModelGetters = listOf(
          Pair({ android().jacoco().version() }, { android().testCoverage().jacocoVersion() })
        ),
        tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.testCoverageUsageInfo.move.tooltipText"),
        usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToTestCoverageRefactoringProcessor.move.usageType")),
      ),
      RemovePropertiesInfo(
        propertyModelListGetter = { listOf(android().jacoco()) },
        tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.testCoverageUsageInfo.remove.tooltipText"),
        usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToTestCoverageRefactoringProcessor.remove.usageType")),
      ),
    ),
  )