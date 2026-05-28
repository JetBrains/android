/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.play.findings.ui

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.OfflineStatusManager
import com.android.tools.idea.insights.OfflineStatusManagerImpl
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.intellij.openapi.project.Project
import icons.StudioIllustrations
import javax.swing.Icon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FindingsTabProvider : AppInsightsTabProvider {
  override val displayName: String = "Quality Findings"

  override val icon: Icon = StudioIllustrations.Common.PLAY_CONSOLE_ICON

  override fun populateTab(project: Project, tabPanel: AppInsightsTabPanel, activeTabFlow: Flow<Boolean>) {}

  // TODO: Temporary stub to prevent background inspection crashes in tests.
  override fun getConfigurationManager(project: Project): AppInsightsConfigurationManager {
    return object : AppInsightsConfigurationManager {
      override val project: Project = project
      override val configuration: StateFlow<AppInsightsModel> = MutableStateFlow(AppInsightsModel.Unauthenticated)
      override val offlineStatusManager: OfflineStatusManager = OfflineStatusManagerImpl()
    }
  }

  override fun isApplicable(): Boolean {
    return StudioFlags.PLAY_FINDINGS_ENABLED.get()
  }
}
