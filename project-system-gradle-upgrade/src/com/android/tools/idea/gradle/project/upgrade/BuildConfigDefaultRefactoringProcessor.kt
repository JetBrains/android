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

class BuildConfigDefaultRefactoringProcessor : AbstractBooleanPropertyDefaultRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val upgradeEventKind = UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.BUILD_CONFIG_DEFAULT
  override val propertyKey = "android.defaults.buildfeatures.buildconfig"
  override val oldDefault = true
  override var necessityInfo = PointNecessity(DEFAULT_CHANGED)

  override val insertPropertyText = AgpUpgradeBundle.message("buildConfigDefaultRefactoringProcessor.enable.usageType")
  override val tooltip = AgpUpgradeBundle.message("buildConfigBuildFeature.enable.tooltipText")
  override val usageViewHeader = AgpUpgradeBundle.message("buildConfigDefaultRefactoringProcessor.usageView.header")
  override val readMoreUrlRedirect = ReadMoreUrlRedirect("build-config-default")

  override fun getRefactoringId() = "com.android.tools.agp.upgrade.buildConfigDefault"
  override fun getCommandName() = AgpUpgradeBundle.message("buildConfigDefaultRefactoringProcessor.commandName")
  override fun getShortDescription() = """
    The default value for buildFeatures.buildConfig is changing, meaning that
    the Android Gradle Plugin will no longer generate BuildConfig classes by default.
    This processor adds a directive to preserve the previous behavior of generating
    BuildConfig classes for all modules; if this project does not use BuildConfig, you
    can remove the android.defaults.buildfeatures.buildconfig property from the
    project's gradle.properties file after this upgrade.
  """.trimIndent()

  companion object {
    val DEFAULT_CHANGED: AgpVersion = AgpVersion.parse("8.0.0-alpha02")
  }
}