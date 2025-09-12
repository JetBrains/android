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
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project

/**
 * Starting with AGP 9.0, the default value of android.r8.optimizedResourceShrinking is now true. This refactoring adds the property if it
 * was not defined and sets it to false when upgrading from a version lower than 9.0.0-alpha01
 */
class R8OptimizedResourceShrinkingDefaultRefactoringProcessor: AbstractBooleanPropertyDefaultRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val propertyKey = "android.r8.optimizedResourceShrinking"
  override val oldDefault = false
  override val upgradeEventKind = UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.USE_R8_OPTIMIZED_RESOURCE_SHRINKING_DEFAULT
  override val insertPropertyText = AgpUpgradeBundle.message("useR8OptimizedResourceShrinking.enable.usageType")
  override val tooltip = AgpUpgradeBundle.message("useR8OptimizedResourceShrinking.tooltipText")
  override val usageViewHeader = AgpUpgradeBundle.message("useR8OptimizedResourceShrinking.usageView.header")
  override val necessityInfo = PointNecessity(DEFAULT_CHANGED)
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.useR8OptimizedResourceShrinking"
  override fun getCommandName() = AgpUpgradeBundle.message("useR8OptimizedResourceShrinking.commandName")
  override val readMoreUrlRedirect: ReadMoreUrlRedirect? = ReadMoreUrlRedirect("r8-optimized-resource-shrinking")
  override fun getShortDescription() = AgpUpgradeBundle.message("useR8OptimizedResourceShrinking.shortDescription")

  companion object {
    val DEFAULT_CHANGED = AgpVersion.parse("9.0.0-alpha01")
  }
}