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

import com.android.tools.idea.compose.preview.findComposePreviewManagerForContext
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.preview.actions.navigateBack
import com.android.tools.idea.preview.modes.PreviewMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons

class StopUiCheckPreviewAction :
  DumbAwareAction(
    message("action.stop.uicheck.title"),
    message("action.stop.uicheck.description"),
    StudioIcons.Compose.Toolbar.STOP_INTERACTIVE_MODE
  ) {
  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    val composePreviewManagers = findComposePreviewManagerForContext(e.dataContext)
    e.presentation.isEnabled = composePreviewManagers?.status()?.isRefreshing != true
    e.presentation.isVisible = composePreviewManagers?.mode?.value is PreviewMode.UiCheck
  }

  override fun actionPerformed(e: AnActionEvent) {
    navigateBack(e)
  }

  // BGT is needed when calling findComposePreviewManagersForContext because it accesses the
  // VirtualFile
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
