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

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.message
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons

class UiCheckDropDownAction :
  DropDownAction(
    message("action.uicheck.toolbar.title"),
    message("action.uicheck.toolbar.description"),
    StudioIcons.LayoutEditor.Properties.VISIBLE
  ) {
  override fun updateActions(context: DataContext): Boolean {
    removeAll()
    context.getData(COMPOSE_PREVIEW_MANAGER)?.let { add(UiCheckFilteringAction(it)) }
    return true
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

internal class UiCheckFilteringAction(private val previewManager: ComposePreviewManager) :
  ToggleAction("Show Previews With Problems Only") {

  override fun isSelected(e: AnActionEvent) = previewManager.isUiCheckFilterEnabled

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    previewManager.isUiCheckFilterEnabled = state
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
