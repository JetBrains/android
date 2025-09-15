/*
 * Copyright (C) 2022 The Android Open Source Project
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

class NonConstantRClassDefaultRefactoringProcessor : AbstractBooleanPropertyDefaultRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val upgradeEventKind = UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.NON_CONSTANT_R_CLASS_DEFAULT
  override val propertyKey = "android.nonFinalResIds"
  override val oldDefault = false
  override var necessityInfo = PointNecessity(DEFAULT_CHANGED)

  override val insertPropertyText = AgpUpgradeBundle.message("nonConstantRClassDefaultRefactoringProcessor.usageType")
  override val tooltip = AgpUpgradeBundle.message("nonConstantRClassUsageInfo.tooltipText")
  override val usageViewHeader = AgpUpgradeBundle.message("nonConstantRClassDefaultRefactoringProcessor.usageView.header")
  override val readMoreUrlRedirect = ReadMoreUrlRedirect("non-constant-r-class-default")

  override fun getCommandName() = AgpUpgradeBundle.message("nonConstantRClassDefaultRefactoringProcessor.commandName")
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.nonConstantRClass"

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.NON_CONSTANT_R_CLASS_DEFAULT)

  override fun getShortDescription() = """
    R classes in applications and tests previously used constant values that can be
    inlined by the java compiler. The default behaviour in the Android Gradle
    plugin is changing to speed up builds and allow more precise shrinking; this
    processor inserts a property in gradle.properties to preserve the current
    default.
  """.trimIndent()


  companion object {
    val DEFAULT_CHANGED = AgpVersion.parse("8.0.0-beta01")
  }
}
