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

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

val MIGRATE_ADB_OPTIONS_TO_INSTALLATION =
  PropertiesOperationsRefactoringInfo(
    optionalFromVersion = AgpVersion.parse("7.0.1"),
    requiredFromVersion = AgpVersion.parse("9.0.0-alpha01"),
    commandNameSupplier = AndroidBundle.messagePointer("project.upgrade.migrateToInstallationRefactoringProcessor.commandName"),
    shortDescriptionSupplier = {
      """
      Configuration related to the adb tool is now performed using
      the installation block.
    """.trimIndent()
    },
    processedElementsHeaderSupplier = AndroidBundle.messagePointer(
      "project.upgrade.migrateToInstallationRefactoringProcessor.usageView.header"),
    componentKind = UpgradeAssistantComponentKind.MIGRATE_TO_INSTALLATION,
    propertiesOperationInfos = listOf(
      MovePropertiesInfo(
        sourceToDestinationPropertyModelGetters = listOf(
          Pair({ android().adbOptions().installOptions() }, { android().installation().installOptions() }),
          Pair({ android().adbOptions().timeOutInMs() }, { android().installation().timeOutInMs() }),
        ),
        tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.installationUsageInfo.move.tooltipText"),
        usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToInstallationRefactoringProcessor.move.usageType"))
      ),
      RemovePropertiesInfo(
        propertyModelListGetter = { listOf(android().adbOptions()) },
        tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.installationUsageInfo.remove.tooltipText"),
        usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToInstallationRefactoringProcessor.remove.usageType"))
      ),
    ),
  )