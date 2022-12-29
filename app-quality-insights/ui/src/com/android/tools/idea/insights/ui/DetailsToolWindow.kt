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
package com.google.services.firebase.insights.ui

import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.google.services.firebase.insights.AppInsightsState
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

object DetailsToolWindow {
  fun create(
    scope: CoroutineScope,
    state: Flow<AppInsightsState>
  ): ToolWindowDefinition<AppInsightsContext> {
    return ToolWindowDefinition(
      "Details",
      StudioIcons.DatabaseInspector.TABLE,
      "APP_INSIGHTS_DETAILS",
      Side.RIGHT,
      Split.TOP,
      AutoHide.DOCKED,
      ToolWindowDefinition.DEFAULT_SIDE_WIDTH,
      ToolWindowDefinition.DEFAULT_BUTTON_SIZE,
      ToolWindowDefinition.ALLOW_BASICS
    ) { DetailsToolWindowContent(scope, state) }
  }
}

private class DetailsToolWindowContent(scope: CoroutineScope, state: Flow<AppInsightsState>) :
  ToolContent<AppInsightsContext> {
  private val component = JPanel(BorderLayout())

  init {
    component.add(DetailsPanel(scope, state), BorderLayout.CENTER)
  }

  override fun dispose() = Unit
  override fun getComponent() = component
  override fun setToolContext(toolContext: AppInsightsContext?) = Unit
}
