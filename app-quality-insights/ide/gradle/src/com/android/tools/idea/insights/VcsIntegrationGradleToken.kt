/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.insights.persistence.AppInsightsSettings
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.cacheInvalidatingOnSyncModifications
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules

class VcsIntegrationGradleToken : VcsIntegrationToken<GradleProjectSystem>, GradleToken {
  override fun canShowSuggestVcsIntegrationFeaturePanel(
    projectSystem: GradleProjectSystem
  ): Boolean {
    val project = projectSystem.project
    if (!StudioFlags.APP_INSIGHTS_VCS_SUPPORT.get()) return false
    if (project.service<AppInsightsSettings>().isSuggestVcsIntegrationDismissed) return false
    if (project.isVcsInfoEnabledInAgp()) return false

    return project.hasRequiredAgpVersion(MIN_SUPPORTED_AGP_VERSION)
  }

  companion object {
    const val MIN_SUPPORTED_AGP_VERSION = "8.2.0-alpha06"
  }

  private fun Project.isVcsInfoEnabledInAgp(): Boolean {
    return cacheInvalidatingOnSyncModifications {
      modules
        .asList()
        .mapNotNull { it.getModuleSystem() as? GradleModuleSystem }
        .any { it.enableVcsInfo }
    }
  }

  private fun Project.hasRequiredAgpVersion(requiredAppVersion: String): Boolean {
    val androidPluginInfo = AndroidPluginInfo.findFromModel(this)?.pluginVersion ?: return false

    return androidPluginInfo >= requiredAppVersion
  }
}
