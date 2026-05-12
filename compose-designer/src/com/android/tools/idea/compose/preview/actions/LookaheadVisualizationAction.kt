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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.message
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

class LookaheadVisualizationAction : ToggleAction(message("action.lookahead.visualization"), null, null) {

  override fun isSelected(e: AnActionEvent): Boolean {
    return e.getData(COMPOSE_PREVIEW_MANAGER)?.isLookaheadAnimationVisualDebuggingEnabled ?: false
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val composePreviewManager = e.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    composePreviewManager.isLookaheadAnimationVisualDebuggingEnabled = state
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.icon = if (isSelected(e)) AllIcons.Actions.Checked else null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class LookaheadLabelsAction : ToggleAction(message("action.lookahead.visualization.labels"), null, null) {

  override fun isSelected(e: AnActionEvent): Boolean {
    return e.getData(COMPOSE_PREVIEW_MANAGER)?.isLookaheadAnimationVisualDebuggingKeyLabelEnabled ?: false
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val composePreviewManager = e.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    composePreviewManager.isLookaheadAnimationVisualDebuggingKeyLabelEnabled = state
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val visualizationEnabled = e.getData(COMPOSE_PREVIEW_MANAGER)?.isLookaheadAnimationVisualDebuggingEnabled ?: false
    e.presentation.isEnabled = visualizationEnabled
    e.presentation.icon = if (isSelected(e)) AllIcons.Actions.Checked else null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
