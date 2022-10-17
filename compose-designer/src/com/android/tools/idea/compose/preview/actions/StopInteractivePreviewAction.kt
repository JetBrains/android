/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnActionButton
import icons.StudioIcons

/**
 * Action to stop the interactive preview (including animation inspection). Only visible when it's already running and if the preview is not
 * refreshing.
 */
class StopInteractivePreviewAction: AnActionButton(message("action.stop.interactive.title"),
                                                   message("action.stop.interactive.description"),
                                                   StudioIcons.Compose.Toolbar.STOP_INTERACTIVE_MODE) {
  override fun displayTextInToolbar(): Boolean = true

  override fun updateButton(e: AnActionEvent) {
    e.presentation.isEnabled = findComposePreviewManagersForContext(e.dataContext).any {
      // The action should be disabled when refreshing.
      !it.status().isRefreshing &&
      (it.status().interactiveMode == ComposePreviewManager.InteractiveMode.READY)
    }
    e.presentation.isVisible = findComposePreviewManagersForContext(e.dataContext).any {
      it.status().interactiveMode != ComposePreviewManager.InteractiveMode.DISABLED
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    findComposePreviewManagersForContext(e.dataContext).forEach {
      it.stopInteractivePreview()
      it.animationInspectionPreviewElementInstance = null
    }
  }
}