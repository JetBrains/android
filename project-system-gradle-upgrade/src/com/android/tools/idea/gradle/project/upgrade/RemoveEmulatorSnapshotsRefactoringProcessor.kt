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
import com.android.tools.idea.gradle.dsl.android.model.android.android
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.usages.impl.rules.UsageType

val REMOVE_EMULATOR_SNAPSHOTS =
  PropertiesOperationsRefactoringInfo(
    optionalFromVersion = AgpVersion.parse("7.0.0-alpha13"),
    requiredFromVersion = AgpVersion.parse("9.0.0-alpha03"),
    commandNameSupplier = AgpUpgradeBundle.messagePointer("removeEmulatorSnapshotsRefactoringProcessor.commandName"),
    shortDescriptionSupplier = { """
        emulatorSnapshots block is no longer supported and deleted.
      """.trimIndent()
    },
    processedElementsHeaderSupplier = AgpUpgradeBundle.messagePointer("removeEmulatorSnapshotsRefactoringProcessor.usageView.header"),
    componentKind = UpgradeAssistantComponentKind.REMOVE_EMULATOR_SNAPSHOTS,
    propertiesOperationInfos = listOf(
      RemovePropertiesInfo(
        propertyModelListGetter = { listOf(failureRetention(), emulatorSnapshots()) },
        tooltipTextSupplier = AgpUpgradeBundle.messagePointer("removeEmulatorSnapshotsRefactoringProcessor.remove.tooltipText"),
        usageType = UsageType(AgpUpgradeBundle.messagePointer("removeEmulatorSnapshotsRefactoringProcessor.remove.usageType"))
      )
    ),
  )

private fun GradleBuildModel.failureRetention() = android().testOptions().failureRetention()
private fun GradleBuildModel.emulatorSnapshots() = android().testOptions().emulatorSnapshots()
