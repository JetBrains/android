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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.insights.AppInsightsState
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

object DistributionToolWindow {
  fun create(
    name: String,
    scope: CoroutineScope,
    state: Flow<AppInsightsState>,
  ): AppInsightsToolWindowDefinition {
    return AppInsightsToolWindowDefinition(
      "Details",
      StudioIcons.AppQualityInsights.DETAILS,
      name,
    ) {
      DetailsToolWindowContent(scope, state)
    }
  }
}

private class DetailsToolWindowContent(scope: CoroutineScope, state: Flow<AppInsightsState>) :
  ToolContent<AppInsightsToolWindowContext> {
  private val component = JPanel(BorderLayout())

  init {
    component.add(DistributionsContainerPanel(scope, state), BorderLayout.CENTER)
  }

  override fun dispose() = Unit

  override fun getComponent() = component

  override fun setToolContext(toolContext: AppInsightsToolWindowContext?) = Unit
}
