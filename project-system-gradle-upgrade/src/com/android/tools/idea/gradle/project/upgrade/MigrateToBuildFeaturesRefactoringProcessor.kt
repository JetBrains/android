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

val VIEW_BINDING_ENABLED_INFO = MovePropertiesInfo(
  sourceToDestinationPropertyModelGetters = listOf(
    Pair({ android().viewBinding().enabled() }, { android().buildFeatures().viewBinding() }),
  ),
  tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.viewBindingEnabledUsageInfo.tooltipText"),
  usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToBuildFeaturesRefactoringProcessor.viewBindingEnabledUsageType"))
)

val DATA_BINDING_ENABLED_INFO = MovePropertiesInfo(
  sourceToDestinationPropertyModelGetters = listOf(
    Pair({ android().dataBinding().enabled() }, { android().buildFeatures().dataBinding() }),
  ),
  tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.dataBindingEnabledUsageInfo.tooltipText"),
  usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToBuildFeaturesRefactoringProcessor.dataBindingEnabledUsageType"))
)

val MIGRATE_TO_BUILD_FEATURES_INFO = PropertiesOperationsRefactoringInfo(
  optionalFromVersion = AgpVersion.parse("4.0.0-alpha05"),
  requiredFromVersion = AgpVersion.parse("7.0.0"),
  commandNameSupplier = AndroidBundle.messagePointer("project.upgrade.migrateToBuildFeaturesRefactoringProcessor.commandName"),
  shortDescriptionSupplier = { """
    The viewBinding and dataBinding features used to be enabled using
    a flag in their respective blocks; they are now enabled using an
    equivalent flag in the buildFeatures block.
  """.trimIndent() },
  processedElementsHeaderSupplier = AndroidBundle.messagePointer("project.upgrade.migrateToBuildFeaturesRefactoringProcessor.usageView.header"),
  componentKind = UpgradeAssistantComponentKind.MIGRATE_TO_BUILD_FEATURES,
  propertiesOperationInfos = listOf(DATA_BINDING_ENABLED_INFO, VIEW_BINDING_ENABLED_INFO)
)

