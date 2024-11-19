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
package com.android.tools.idea.insights.ui.insight

import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.ui.AppInsightsToolWindowContext
import com.android.tools.idea.insights.ui.InsightPermissionDeniedHandler
import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.JPanel

object InsightToolWindow {
  fun create(
    projectController: AppInsightsProjectLevelController,
    parentDisposable: Disposable,
    permissionDeniedHandler: InsightPermissionDeniedHandler,
  ): ToolWindowDefinition<AppInsightsToolWindowContext> {
    return ToolWindowDefinition(
      "Insights",
      StudioIcons.StudioBot.LOGO_MONOCHROME,
      "APP_INSIGHTS_INSIGHTS",
      Side.RIGHT,
      Split.TOP,
      AutoHide.DOCKED,
      JBUI.scale(400),
      ToolWindowDefinition.DEFAULT_BUTTON_SIZE,
      ToolWindowDefinition.ALLOW_BASICS,
    ) {
      InsightToolWindowContent(projectController, parentDisposable, permissionDeniedHandler)
    }
  }
}

private class InsightToolWindowContent(
  projectController: AppInsightsProjectLevelController,
  parentDisposable: Disposable,
  permissionDeniedHandler: InsightPermissionDeniedHandler,
) : ToolContent<AppInsightsToolWindowContext> {
  private val component = JPanel(BorderLayout())

  init {
    component.add(
      InsightMainPanel(projectController, parentDisposable, permissionDeniedHandler),
      BorderLayout.CENTER,
    )
  }

  override fun dispose() = Unit

  override fun getComponent() = component

  override fun setToolContext(toolContext: AppInsightsToolWindowContext?) = Unit
}
