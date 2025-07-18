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
package com.android.tools.idea.streaming.uisettings.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.streaming.device.DEVICE_VIEW_KEY
import com.android.tools.idea.streaming.device.actions.AbstractDeviceAction
import com.android.tools.idea.streaming.device.actions.getDeviceConfig
import com.android.tools.idea.streaming.device.actions.getDeviceController
import com.android.tools.idea.streaming.uisettings.DeviceUiSettingsController
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsDialog
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension

/** Opens a picker with UI settings of a physical device. */
internal class DeviceUiSettingsAction : AbstractDeviceAction(
  configFilter = {
    it.apiLevel >= 33
    && it.deviceProperties.resolution != null
    && it.deviceProperties.density != null
    && it.deviceType != DeviceType.AUTOMOTIVE
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
    deviceView.createCoroutineScope().launch {
      controller.populateModel()
      withContext(Dispatchers.EDT) {
        val dialog = UiSettingsDialog(project, model, deviceType, deviceView)
        dialog.show()
      }
    }
  }
}
