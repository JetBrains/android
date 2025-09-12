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
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project

/**
 * This processor sets the android.newDsl Gradle property to false in upgrades over the 9.0 boundary, which
 * allows the use of the external Kotlin Gradle plugin to continue to work.
 */
class NewDslDefaultRefactoringProcessor : AbstractBooleanPropertyDefaultRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val propertyKey: String = "android.newDsl"
  override val oldDefault: Boolean = false
  override val upgradeEventKind: UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind =
    UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.NEWDSL_DEFAULT
  override val insertPropertyText: String = AgpUpgradeBundle.message("newDsl.usage.name")
  override val tooltip: String = AgpUpgradeBundle.message("newDsl.usage.tooltipText")
  override val usageViewHeader: String = AgpUpgradeBundle.message("newDsl.usageViewHeader")
  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.android.newDsl.default"
  override val necessityInfo: AgpUpgradeComponentNecessityInfo = PointNecessity(DEFAULT_CHANGED)
  override fun getCommandName(): String = AgpUpgradeBundle.message("newDsl.commandName")

  companion object {
    val DEFAULT_CHANGED = AgpVersion.parse("9.0.0-alpha04")
  }
}