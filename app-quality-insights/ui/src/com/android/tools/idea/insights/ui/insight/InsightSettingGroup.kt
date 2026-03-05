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

import com.android.tools.idea.insights.ai.AiInsightToolkit
import com.android.tools.idea.ui.resourcemanager.actions.HeaderAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction

class InsightSettingGroup(private val aiInsightToolkit: AiInsightToolkit) : DefaultActionGroup() {

  init {
    isPopup = true
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.General.Settings
  }

  override fun getChildren(e: AnActionEvent?) =
    arrayOf(HeaderAction("AI Insights Settings", null), InsightAutoGenerateSetting(aiInsightToolkit))
}

class InsightAutoGenerateSetting(private val aiInsightToolkit: AiInsightToolkit) : ToggleAction("Auto-generate insight summaries") {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  @Suppress("UnstableApiUsage")
  override fun createTemplatePresentation() = super.createTemplatePresentation().apply { keepPopupOnPerform = KeepPopupOnPerform.Never }

  override fun isSelected(e: AnActionEvent) = aiInsightToolkit.isAutoGenerateEnabled()

  override fun setSelected(e: AnActionEvent, state: Boolean) = aiInsightToolkit.setAutoGenerate(state)
}
