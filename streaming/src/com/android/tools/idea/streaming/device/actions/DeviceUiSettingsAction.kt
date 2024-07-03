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
package com.android.tools.idea.streaming.device.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.device.DEVICE_VIEW_KEY
import com.android.tools.idea.streaming.device.DeviceUiSettingsController
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.android.tools.idea.streaming.uisettings.ui.showUiSettingsPopup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.EventQueue

private val isSettingsPickerEnabled: Boolean
  get() = StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.get()

/**
 * Opens a picker with UI settings of a physical device.
 */
internal class DeviceUiSettingsAction : AbstractDeviceAction(
  configFilter = {
    it.apiLevel >= 33
    && isSettingsPickerEnabled
    && it.deviceProperties.resolution != null
    && it.deviceProperties.density != null
  }
) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val deviceView = event.getData(DEVICE_VIEW_KEY) ?: return
    val project = event.project ?: return
    val deviceController = getDeviceController(event) ?: return
    val config = getDeviceConfig(event) ?: return
    val deviceType = config.deviceProperties.deviceType ?: DeviceType.HANDHELD
    val screenSize = config.deviceProperties.resolution?.let { Dimension(it.width, it.height) } ?: return
    val density = config.deviceProperties.density ?: return
    val model = UiSettingsModel(screenSize, density, config.apiLevel, deviceType)
    val controller = DeviceUiSettingsController(deviceController, config, project, model, deviceView)
    AndroidCoroutineScope(deviceView).launch {
      controller.populateModel()
      EventQueue.invokeLater {
        val panel = UiSettingsPanel(model, deviceType)
        showUiSettingsPopup(panel, this@DeviceUiSettingsAction, event, deviceView)
      }
    }
  }
}
