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
package com.android.tools.idea.streaming.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.actions.enableRichTooltip
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.xr.XrInputMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

/** Sets an input mode for an XR AVD. */
sealed class StreamingXrInputModeAction(private val inputMode: XrInputMode) : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean =
    getXrInputController(event)?.inputMode == inputMode

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    if (state) {
      getXrInputController(event)?.inputMode = inputMode
      val displayView = getDisplayView(event) ?: return
      event.project?.service<HardwareInputStateStorage>()?.setHardwareInputEnabled(displayView.deviceId, false)
      displayView.hardwareInputStateChanged(event, false)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabledAndVisible = getDeviceType(event) == DeviceType.XR &&
                                             (inputMode != XrInputMode.HAND || StudioFlags.EMBEDDED_EMULATOR_XR_HAND_TRACKING.get()) &&
                                             (inputMode != XrInputMode.EYE || StudioFlags.EMBEDDED_EMULATOR_XR_EYE_TRACKING.get())
    event.presentation.enableRichTooltip(this)
  }

  class HandTracking : StreamingXrInputModeAction(XrInputMode.HAND)
  class EyeTracking : StreamingXrInputModeAction(XrInputMode.EYE)
  class ViewDirection : StreamingXrInputModeAction(XrInputMode.VIEW_DIRECTION)
  class LocationInSpaceXY : StreamingXrInputModeAction(XrInputMode.LOCATION_IN_SPACE_XY)
  class LocationInSpaceZ : StreamingXrInputModeAction(XrInputMode.LOCATION_IN_SPACE_Z)
}