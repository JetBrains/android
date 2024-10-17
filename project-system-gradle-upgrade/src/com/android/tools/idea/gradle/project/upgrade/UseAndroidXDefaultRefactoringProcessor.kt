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
import org.jetbrains.android.util.AndroidBundle

/**
 * Starting with AGP 9.0, the default value of android.useAndroidX is now true. This refactoring adds the property if it was not defined and
 * sets it to false when upgrading from a version lower than 9.0..0-alpha01
 */
class UseAndroidXDefaultRefactoringProcessor: AbstractBooleanPropertyDefaultRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val propertyKey = "android.useAndroidX"
  override val oldDefault = false
  override val upgradeEventKind = UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.USE_ANDROIDX_DEFAULT
  override val insertPropertyText = AndroidBundle.message("project.upgrade.useAndroidXDefaultRefactoringProcessor.enable.usageType")!!
  override val tooltip = AndroidBundle.message("project.upgrade.useAndroidXDefaultRefactoringProcessor.tooltipText")!!
  override val usageViewHeader = AndroidBundle.message("project.upgrade.useAndroidXDefaultRefactoringProcessor.usageView.header")!!
  override val necessityInfo = PointNecessity(AgpVersion.parse("9.0.0-alpha01"))
  override val readMoreUrlRedirect = ReadMoreUrlRedirect("use-androidx-default")
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.useAndroidX"
  override fun getCommandName() = AndroidBundle.message("project.upgrade.useAndroidXDefaultRefactoringProcessor.commandName")!!
  override fun getShortDescription() = AndroidBundle.message("project.upgrade.useAndroidXDefaultRefactoringProcessor.shortDescription")!!
}