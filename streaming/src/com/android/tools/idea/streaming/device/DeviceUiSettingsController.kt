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
package com.android.tools.idea.streaming.device

import com.android.tools.idea.streaming.uisettings.ui.UiSettingsController
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel

/**
 * A controller for the UI settings for a physical device,
 * that populates the model and reacts to changes to the model initiated by the UI.
 */
internal class DeviceUiSettingsController(
  private val deviceController: DeviceController,
  model: UiSettingsModel
) : UiSettingsController(model) {

  override suspend fun populateModel() {
    val response = deviceController.getUiSettings()
    model.inDarkMode.setFromController(response.darkMode)
  }

  override fun setDarkMode(on: Boolean) {
    deviceController.sendControlMessage(SetDarkModeMessage(on))
  }
}
