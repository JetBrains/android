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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction

/** Action to toggle the tooltip inspection mode of compose preview. */
class ShowInspectionTooltipsAction(private val composeContext: DataContext) : ToggleAction() {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.text = message("action.scene.view.control.show.inspection.tooltip")
    e.presentation.isEnabledAndVisible = StudioFlags.COMPOSE_VIEW_INSPECTOR.get()
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return e.withDataContext(composeContext)
      .getData(COMPOSE_PREVIEW_MANAGER)
      ?.isInspectionTooltipEnabled
      ?: false
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.withDataContext(composeContext).getData(COMPOSE_PREVIEW_MANAGER)?.isInspectionTooltipEnabled =
      state
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
