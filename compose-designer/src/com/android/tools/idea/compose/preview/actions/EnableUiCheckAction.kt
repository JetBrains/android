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

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.lite.ComposePreviewLiteModeManager
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkCancelDialog
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
    val isLiteModeEnabled = ComposePreviewLiteModeManager.isLiteModeEnabled
    e.presentation.isVisible = isUiCheckModeEnabled
    e.presentation.isEnabled = isUiCheckModeEnabled && !isLiteModeEnabled
    e.presentation.text = if (isLiteModeEnabled) null else message("action.uicheck.title")
    e.presentation.description =
      if (isLiteModeEnabled) message("action.uicheck.lite.mode.description")
      else message("action.uicheck.description")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val modelDataContext = dataContextProvider()
    val manager = modelDataContext.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    val instanceId = modelDataContext.getData(COMPOSE_PREVIEW_ELEMENT_INSTANCE) ?: return

    val answer =
      showOkCancelDialog(
        title = message("action.uicheck.dialog.title"),
        message = message("action.uicheck.dialog.description"),
        okText = message("action.uicheck.dialog.oktext"),
        icon = Messages.getInformationIcon()
      )
    if (answer == Messages.CANCEL) {
      return
    }
    manager.startUiCheckPreview(instanceId)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
