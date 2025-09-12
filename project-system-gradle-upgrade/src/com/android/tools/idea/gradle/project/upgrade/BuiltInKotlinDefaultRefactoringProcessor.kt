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
import org.jetbrains.annotations.TestOnly

/**
 * [AbstractBooleanPropertyDefaultRefactoringProcessor] for the `android.builtInKotlin` Boolean
 * property.
 */
class BuiltInKotlinDefaultRefactoringProcessor : AbstractBooleanPropertyDefaultRefactoringProcessor {

  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  @TestOnly
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)

  override val upgradeEventKind = UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.BUILT_IN_KOTLIN_DEFAULT
  override val propertyKey = "android.builtInKotlin"
  override val oldDefault = false
  override val necessityInfo = PointNecessity(AgpVersion.parse("9.0.0-alpha03"))

  override val insertPropertyText = AgpUpgradeBundle.message("project.upgrade.builtInKotlin.default.usageType")!!
  override val tooltip = AgpUpgradeBundle.message("project.upgrade.builtInKotlin.default.usageType")!!
  override val usageViewHeader = AgpUpgradeBundle.message("project.upgrade.builtInKotlin.default.commandName")!!

  override fun getRefactoringId() = "com.android.tools.agp.upgrade.builtInKotlin.default"
  override fun getCommandName() = AgpUpgradeBundle.message("project.upgrade.builtInKotlin.default.commandName")!!
  override fun getShortDescription() = AgpUpgradeBundle.message("project.upgrade.builtInKotlin.default.shortDescription")!!
}
