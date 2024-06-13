/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.actions

import com.android.tools.configurations.Configuration
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.actions.DesignerActions
import com.android.tools.idea.actions.DeviceMenuAction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.editor.NlActionManager
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.surface.isActionSupported
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

abstract class SwitchDeviceAction : AnAction() {
  final override fun update(e: AnActionEvent) {
    if (isActionEventFromJTextField(e)) {
      e.presentation.isEnabled = false
      return
    }

    if (!e.getData(DESIGN_SURFACE).isActionSupported(NlSupportedActions.SWITCH_DEVICE)) {
      e.presentation.isEnabled = false
      return
    }

    val surface = e.getData(NlActionManager.LAYOUT_EDITOR)
    if (surface != null) {
      val config = surface.configurations.firstOrNull()
      if (config != null) {
        e.presentation.isEnabled = true
        return
      }
    }
    e.presentation.isEnabled = false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getData(NlActionManager.LAYOUT_EDITOR) ?: return
    val config = surface.configurations.firstOrNull() ?: return
    switchDevice(surface, config)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  abstract fun switchDevice(surface: DesignSurface<*>, config: Configuration)
}

/** Change {@link DesignSurface}'s device list to next one. */
class NextDeviceAction private constructor() : SwitchDeviceAction() {

  override fun switchDevice(surface: DesignSurface<*>, config: Configuration) {
    val devices = getSortedDevices(config)
    if (devices.isEmpty()) {
      return
    }
    val currentDevice = config.device
    val nextDevice =
      when (val index = devices.indexOf(config.device)) {
        -1 -> devices.first() // If current device is not in the list, we navigate to first device
        devices.lastIndex -> devices.first()
        else -> devices[index + 1]
      }
    config.setDevice(nextDevice, true)
    if (currentDevice != nextDevice) {
      surface.zoomController.zoomToFit()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): NextDeviceAction {
      return ActionManager.getInstance().getAction(DesignerActions.ACTION_NEXT_DEVICE)
        as NextDeviceAction
    }
  }
}

class PreviousDeviceAction private constructor() : SwitchDeviceAction() {

  override fun switchDevice(surface: DesignSurface<*>, config: Configuration) {
    val devices = getSortedDevices(config)
    if (devices.isEmpty()) {
      return
    }
    val currentDevice = config.device
    val previousDevice =
      when (val index = devices.indexOf(config.device)) {
        -1 -> devices.first() // If current device is not in the list, we navigate to first device
        0 -> devices.last()
        else -> devices[index - 1]
      }
    config.setDevice(previousDevice, true)
    if (currentDevice != previousDevice) {
      surface.zoomController.zoomToFit()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): PreviousDeviceAction {
      return ActionManager.getInstance().getAction(DesignerActions.ACTION_PREVIOUS_DEVICE)
        as PreviousDeviceAction
    }
  }
}

private fun getSortedDevices(config: Configuration) = DeviceMenuAction.getSortedMajorDevices(config)
