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
package com.android.tools.idea.streaming.actions

import com.android.tools.idea.streaming.xr.XrInputMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import icons.StudioIcons

/** Displays a popup menu of XR input modes. */
internal class StreamingXrInputModePopupGroup : DefaultActionGroup(), Toggleable {

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val controller = getXrInputController(event)
    if (controller?.isXrInputAvailable != true || !isHandOrEyeTrackingEnabled(event)) {
      presentation.isEnabledAndVisible = false
      return
    }

    val inputMode = controller.inputMode
    presentation.icon =
      when (inputMode) {
        XrInputMode.HAND -> StudioIcons.Emulator.XR.HAND_TRACKING
        XrInputMode.EYE -> StudioIcons.Emulator.XR.EYE_GAZE
        else -> StudioIcons.Emulator.XR.INTERACT
      }

    Toggleable.setSelected(presentation, inputMode == XrInputMode.HAND || inputMode == XrInputMode.EYE || inputMode == XrInputMode.MOUSE)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
