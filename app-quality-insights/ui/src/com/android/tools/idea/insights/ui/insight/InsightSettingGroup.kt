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
package com.android.tools.idea.insights.ui.insight

import com.android.tools.idea.insights.ui.AI_INSIGHT_TOOLKIT_KEY
import com.android.tools.idea.insights.ui.APP_INSIGHTS_TRACKER_KEY
import com.android.tools.idea.insights.ui.SELECTED_APP_ID_KEY
import com.android.tools.idea.ui.resourcemanager.actions.HeaderAction
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.GenerateInsightsAction.Action
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction

class InsightSettingGroup : DefaultActionGroup() {

  init {
    isPopup = true
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.General.Settings
  }

  override fun getChildren(e: AnActionEvent?) = arrayOf(HeaderAction("AI Insights Settings", null), InsightAutoGenerateSetting())
}

class InsightAutoGenerateSetting : ToggleAction("Auto-generate insight summaries") {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  @Suppress("UnstableApiUsage")
  override fun createTemplatePresentation() = super.createTemplatePresentation().apply { keepPopupOnPerform = KeepPopupOnPerform.Never }

  override fun isSelected(e: AnActionEvent) = e.getData(AI_INSIGHT_TOOLKIT_KEY)?.isAutoGenerateEnabled() ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val aiInsightToolkit = e.getData(AI_INSIGHT_TOOLKIT_KEY) ?: return
    aiInsightToolkit.setAutoGenerate(state)

    val tracker = e.getData(APP_INSIGHTS_TRACKER_KEY) ?: return
    val appId = e.getData(SELECTED_APP_ID_KEY) ?: return

    tracker.logGenerateInsightAction(
      appId,
      if (state) {
        Action.ENABLE_AUTO_GENERATE
      } else {
        Action.DISABLE_AUTO_GENERATE
      },
    )
  }
}
