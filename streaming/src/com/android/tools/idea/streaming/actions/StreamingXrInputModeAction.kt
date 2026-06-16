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
import com.android.tools.idea.streaming.core.FloatingToolbarContainer
import com.android.tools.idea.streaming.core.STREAMING_DEVICE_ID_KEY
import com.android.tools.idea.streaming.xr.XrInputMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/** Sets an input mode for an XR AVD. */
sealed class StreamingXrInputModeAction(private val inputMode: XrInputMode) : ToggleAction(), DumbAware {

  init {
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never // Don't keep the popup open after selecting an input mode.
  }

  override fun isSelected(event: AnActionEvent): Boolean = getXrInputController(event)?.inputMode == inputMode

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    if (state) {
      getXrInputController(event)?.inputMode = inputMode
    } else {
      FloatingToolbarContainer.triggerActivation(event)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    val isHandAndEyeTrackingEnabled = isHandAndEyeTrackingEnabled(event)
    event.presentation.isEnabledAndVisible =
      getDeviceType(event) == DeviceType.XR_HEADSET &&
        (inputMode != XrInputMode.HAND || isHandAndEyeTrackingEnabled) &&
        (inputMode != XrInputMode.EYE || isHandAndEyeTrackingEnabled)
    event.presentation.enableRichTooltip(this)
  }

  class InteractionMouse : StreamingXrInputModeAction(XrInputMode.MOUSE) {

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      super.setSelected(event, state)
      // Mouse interaction mode enables hardware input.
      val project = event.project ?: return
      val deviceId = event.getData(STREAMING_DEVICE_ID_KEY) ?: return
      HardwareInputStateStorage.getInstance(project).setHardwareInputEnabled(deviceId, state)
    }

    override fun update(event: AnActionEvent) {
      super.update(event)
      if (!isHandAndEyeTrackingEnabled(event)) {
        event.presentation.isEnabledAndVisible = false
      }
    }
  }

  class InteractionHand : StreamingXrInputModeAction(XrInputMode.HAND)

  class InteractionEye : StreamingXrInputModeAction(XrInputMode.EYE)

  class ViewDirection : StreamingXrInputModeAction(XrInputMode.VIEW_DIRECTION)

  class LocationInSpaceXY : StreamingXrInputModeAction(XrInputMode.LOCATION_IN_SPACE_XY)

  class LocationInSpaceZ : StreamingXrInputModeAction(XrInputMode.LOCATION_IN_SPACE_Z)

  class Interaction : StreamingXrInputModeAction(XrInputMode.MOUSE) {

    override fun update(event: AnActionEvent) {
      super.update(event)
      if (isHandAndEyeTrackingEnabled(event)) {
        event.presentation.isEnabledAndVisible = false
      }
    }
  }
}

internal fun isHandAndEyeTrackingEnabled(event: AnActionEvent): Boolean = getXrInputController(event)?.isHandAndEyeInputSupported ?: false
