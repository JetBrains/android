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

import com.android.tools.idea.res.AppLanguageService
import com.android.tools.idea.streaming.uisettings.data.AppLanguage
import com.android.tools.idea.streaming.uisettings.stats.UiSettingsStats
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsController
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.intellij.openapi.project.Project

/**
 * A controller for the UI settings for a physical device,
 * that populates the model and reacts to changes to the model initiated by the UI.
 */
internal class DeviceUiSettingsController(
  private val deviceController: DeviceController,
  deviceConfig: DeviceConfiguration,
  private val project: Project,
  model: UiSettingsModel
) : UiSettingsController(model, UiSettingsStats(deviceConfig.deviceProperties.deviceInfoProto)) {

  override suspend fun populateModel() {
    val response = deviceController.getUiSettings()
    model.inDarkMode.setFromController(response.darkMode)
    model.gestureOverlayInstalled.setFromController(response.gestureOverlayInstalled)
    model.gestureNavigation.setFromController(response.gestureNavigation)
    model.talkBackInstalled.setFromController(response.tackBackInstalled)
    model.talkBackOn.setFromController(response.talkBackOn)
    model.selectToSpeakOn.setFromController(response.selectToSpeakOn)
    model.fontSizeSettable.setFromController(response.fontSizeSettable)
    model.fontSizeInPercent.setFromController(response.fontSize)
    model.screenDensitySettable.setFromController(response.densitySettable)
    model.screenDensity.setFromController(response.density)
    val languageInfo = AppLanguageService.getInstance(project).getAppLanguageInfo().associateBy { it.applicationId }
    languageInfo[response.foregroundApplicationId]?.localeConfig?.let { config ->
      addLanguage(response.foregroundApplicationId, config, response.appLocale)
    }
  }

  override fun setDarkMode(on: Boolean) {
    deviceController.sendControlMessage(SetDarkModeMessage(on))
  }

  override fun setGestureNavigation(on: Boolean) {
    deviceController.sendControlMessage(SetGestureNavigationMessage(on))
  }

  override fun setAppLanguage(applicationId: String, language: AppLanguage?) {
    deviceController.sendControlMessage(SetAppLanguageMessage(applicationId, language?.tag ?: ""))
  }

  override fun setTalkBack(on: Boolean) {
    deviceController.sendControlMessage(SetTalkBackMessage(on))
  }

  override fun setSelectToSpeak(on: Boolean) {
    deviceController.sendControlMessage(SetSelectToSpeakMessage(on))
  }

  override fun setFontSize(percent: Int) {
    deviceController.sendControlMessage(SetFontSizeMessage(percent))
  }

  override fun setScreenDensity(density: Int) {
    deviceController.sendControlMessage(SetScreenDensityMessage(density))
  }

  override fun reset() {
    // Noop. A device agent will reset all settings when the device is disconnected.
  }
}
