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

import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.essentials.ComposePreviewEssentialsModeManager
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.ui.AnActionButton
import icons.StudioIcons

class EnableUiCheckAction(private val dataContextProvider: () -> DataContext) :
  AnActionButton(
    message("action.uicheck.title"),
    message("action.uicheck.description"),
    StudioIcons.Compose.Toolbar.INSPECT_PREVIEW
  ) {

  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    val isUiCheckModeEnabled = StudioFlags.NELE_COMPOSE_UI_CHECK_MODE.get()
    val isEssentialsModeEnabled = ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
    e.presentation.isVisible = isUiCheckModeEnabled
    e.presentation.isEnabled = isUiCheckModeEnabled && !isEssentialsModeEnabled
    e.presentation.text = if (isEssentialsModeEnabled) null else message("action.uicheck.title")
    e.presentation.description =
      if (isEssentialsModeEnabled) message("action.uicheck.essentials.mode.description")
      else message("action.uicheck.description")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val modelDataContext = dataContextProvider()
    val manager = modelDataContext.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    val instanceId = modelDataContext.getData(COMPOSE_PREVIEW_ELEMENT_INSTANCE) ?: return
    manager.startUiCheckPreview(instanceId)
    e.project?.let {
      IssuePanelService.getInstance(it)
        .setIssuePanelVisibility(true, IssuePanelService.Tab.DESIGN_TOOLS)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
