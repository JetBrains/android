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

val SOURCE_SET_JNI_INFO = RemovePropertiesInfo(
  propertyModelListGetter = { android().sourceSets().map { sourceSet -> sourceSet.jni() } },
  tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.sourceSetJniUsageInfo.tooltipText"),
  usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.sourceSetJniUsageInfo.usageType"))
)

val REMOVE_SOURCE_SET_JNI_INFO = PropertiesOperationsRefactoringInfo(
  optionalFromVersion = AgpVersion.parse("7.0.0-alpha06"),
  requiredFromVersion = AgpVersion.parse("8.0.0"),
  commandNameSupplier = AndroidBundle.messagePointer("project.upgrade.removeSourceSetJniRefactoringProcessor.commandName"),
  shortDescriptionSupplier = { """
    The jni block in an android sourceSet does nothing, and will be removed
    in Android Gradle Plugin version 8.0.0.
  """.trimIndent() },
  processedElementsHeaderSupplier = AndroidBundle.messagePointer("project.upgrade.removeSourceSetJniRefactoringProcessor.usageView.header"),
  componentKind = UpgradeAssistantComponentKind.REMOVE_SOURCE_SET_JNI,
  propertiesOperationInfos = listOf(SOURCE_SET_JNI_INFO)
)
