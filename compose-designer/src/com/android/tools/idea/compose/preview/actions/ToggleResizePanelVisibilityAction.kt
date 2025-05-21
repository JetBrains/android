/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.resize.ResizePanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey

val RESIZE_PANEL_INSTANCE_KEY = DataKey.create<ResizePanel>("ResizePanel")

/**
 * An action that allows users to make the [ResizePanel] visible if it is currently hidden.
 *
 * When performed, this action toggles the visibility of the [ResizePanel], effectively making it
 * visible as it's only actionable when the panel is hidden.
 */
class ToggleResizePanelVisibilityAction : AnAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val resizePanel = e.dataContext.getData(RESIZE_PANEL_INSTANCE_KEY)

    e.presentation.isEnabledAndVisible = false

    if (project == null || resizePanel == null) {
      return
    }

    if (!resizePanel.isVisible) {
      e.presentation.isEnabledAndVisible = true
      e.presentation.text = message("action.toggle.resize.panel.visibility.action.show.text")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val resizePanel = e.dataContext.getData(RESIZE_PANEL_INSTANCE_KEY)
    resizePanel?.let { it.isVisible = !it.isVisible }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
