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

val BUILD_TYPE_USE_PROGUARD_INFO = RemovePropertiesInfo(
  propertyModelListGetter = { android().buildTypes().map { buildType -> buildType.useProguard() } },
  tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.removeBuildTypeUseProguard.tooltipText"),
  usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.removeBuildTypeUseProguard.usageType"))
)

val REMOVE_BUILD_TYPE_USE_PROGUARD_INFO = PropertiesOperationsRefactoringInfo(
  optionalFromVersion = AgpVersion.parse("3.5.0"),
  requiredFromVersion = AgpVersion.parse("7.0.0-alpha14"),
  commandNameSupplier = AndroidBundle.messagePointer("project.upgrade.removeBuildTypeUseProguardRefactoringProcessor.commandName"),
  shortDescriptionSupplier = { """
    The useProguard setting for build types is not supported in Android
    Gradle Plugin version 7.0.0 and higher; from that version the R8 minifier
    is used unconditionally.
  """.trimIndent()},
  processedElementsHeaderSupplier = AndroidBundle.messagePointer("project.upgrade.removeBuildTypeUseProguardRefactoringProcessor.usageView.header"),
  componentKind = UpgradeAssistantComponentKind.REMOVE_BUILD_TYPE_USE_PROGUARD,
  propertiesOperationInfos = listOf(BUILD_TYPE_USE_PROGUARD_INFO)
)

