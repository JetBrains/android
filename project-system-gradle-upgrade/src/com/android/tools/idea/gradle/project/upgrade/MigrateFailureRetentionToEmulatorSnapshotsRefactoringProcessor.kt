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
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

val MIGRATE_FAILURE_RETENTION_TO_EMULATOR_SNAPSHOTS =
  PropertiesOperationsRefactoringInfo(
    optionalFromVersion = AgpVersion.parse("7.0.0-alpha13"),
    requiredFromVersion = AgpVersion.parse("9.0.0-alpha01"),
    commandNameSupplier = AndroidBundle.messagePointer("project.upgrade.migrateToEmulatorSnapshotsRefactoringProcessor.commandName"),
    shortDescriptionSupplier = { """
        Configuration related to retention of snapshots from the emulator is
        now performed using the emulatorSnapshots block.
      """.trimIndent()
    },
    processedElementsHeaderSupplier = AndroidBundle.messagePointer("project.upgrade.migrateToEmulatorSnapshotsRefactoringProcessor.usageView.header"),
    componentKind = UpgradeAssistantComponentKind.MIGRATE_TO_EMULATOR_SNAPSHOTS,
    propertiesOperationInfos = listOf(
      MovePropertiesInfo(
        sourceToDestinationPropertyModelGetters = listOf(
          Pair({ failureRetention().enable() }, { emulatorSnapshots().enableForTestFailures() }),
          Pair({ failureRetention().maxSnapshots() }, { emulatorSnapshots().maxSnapshotsForTestFailures() }),
        ),
        tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.emulatorSnapshotsUsageInfo.move.tooltipText"),
        usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToEmulatorSnapshotsRefactoringProcessor.move.usageType")),
      ),
      RemovePropertiesInfo(
        propertyModelListGetter = { listOf(failureRetention()) },
        tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.emulatorSnapshotsUsageInfo.remove.tooltipText"),
        usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToEmulatorSnapshotsRefactoringProcessor.remove.usageType")),
      ),
    ),
  )

private fun GradleBuildModel.failureRetention() = android().testOptions().failureRetention()
private fun GradleBuildModel.emulatorSnapshots() = android().testOptions().emulatorSnapshots()
