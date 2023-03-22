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
package com.android.tools.idea.vitals.ui

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.intellij.openapi.project.Project
import icons.StudioIcons
import java.time.Clock

class VitalsTabProvider : AppInsightsTabProvider {
  override val tabDisplayName = "Play Vitals"

  // TODO(b/271918057): use real icon.
  override val tabIcon = StudioIcons.Avd.DEVICE_PLAY_STORE

  override fun populateTab(project: Project, tabPanel: AppInsightsTabPanel) {
    tabPanel.setComponent(VitalsTab(VitalsConfigurationManager(project), Clock.systemDefaultZone()))
  }

  override fun isApplicable() = StudioFlags.PLAY_VITALS_ENABLED.get()
}
