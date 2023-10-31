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
package com.android.tools.idea.insights.vcs

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.hasRequiredAgpVersion
import com.android.tools.idea.insights.persistence.AppInsightsSettings
import com.android.tools.idea.projectsystem.cacheInvalidatingOnSyncModifications
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ui.JBUI.CurrentTheme.Banner

internal const val VCS_INTEGRATION_LEARN_MORE_LINK =
  "https://d.android.com/r/studio-ui/debug/aqi-vcs"

class SuggestVcsIntegrationFeaturePanel(val project: Project) :
  EditorNotificationPanel(Banner.INFO_BORDER_COLOR) {
  init {
    text = "Improve code navigation by enabling version control metadata sharing."

    createActionLabel("Learn more") { BrowserUtil.browse(VCS_INTEGRATION_LEARN_MORE_LINK) }

    createActionLabel("Don't show again") {
      isVisible = false
      project.service<AppInsightsSettings>().isSuggestVcsIntegrationDismissed = true
    }

    setCloseAction { isVisible = false }

    isVisible = true
  }

  companion object {
    const val MIN_SUPPORTED_AGP_VERSION = "8.2.0-alpha06"

    fun canShow(project: Project): Boolean {
      if (!StudioFlags.APP_INSIGHTS_VCS_SUPPORT.get()) return false
      if (project.service<AppInsightsSettings>().isSuggestVcsIntegrationDismissed) return false
      if (project.isVcsInfoEnabledInAgp()) return false

      return project.hasRequiredAgpVersion(MIN_SUPPORTED_AGP_VERSION)
    }
  }
}

fun Project.isVcsInfoEnabledInAgp(): Boolean {
  return cacheInvalidatingOnSyncModifications {
    modules.asList().any { it.getModuleSystem().enableVcsInfo }
  }
}
