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

import com.android.sdklib.devices.Device
import com.android.tools.idea.actions.SCENE_VIEW
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons

class EnableUiCheckAction :
  DumbAwareAction(
    message("action.uicheck.title"),
    message("action.uicheck.description"),
    StudioIcons.Compose.Toolbar.UI_CHECK,
  ) {

  override fun update(e: AnActionEvent) {
    val isUiCheckModeEnabled = StudioFlags.COMPOSE_UI_CHECK_MODE.get()
    val isEssentialsModeEnabled = PreviewEssentialsModeManager.isEssentialsModeEnabled
    val disableForWear =
      Device.isWear(e.getData(SCENE_VIEW)?.configuration?.device) &&
        !StudioFlags.COMPOSE_UI_CHECK_FOR_WEAR.get()
    e.presentation.isVisible = isUiCheckModeEnabled
    e.presentation.isEnabled = isUiCheckModeEnabled && !isEssentialsModeEnabled && !disableForWear
    e.presentation.text =
      if (isEssentialsModeEnabled || disableForWear) null else message("action.uicheck.title")
    e.presentation.description =
      if (isEssentialsModeEnabled) message("action.uicheck.essentials.mode.description")
      else if (disableForWear) message("action.uicheck.wear.description")
      else message("action.uicheck.description")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val modelDataContext = e.dataContext
    val manager = modelDataContext.getData(PreviewModeManager.KEY) ?: return
    val instance = modelDataContext.previewElement() ?: return
    val device = modelDataContext.getData(SCENE_VIEW)?.configuration?.device
    val isWearDevice = Device.isWear(device)
    manager.setMode(PreviewMode.UiCheck(baseInstance = UiCheckInstance(instance, isWearDevice)))

    e.project?.let { ProblemsViewToolWindowUtils.selectTab(it, instance.instanceId) }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
