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

import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider.RunningApplicationIdentity
import com.android.tools.idea.res.AppLanguageService
import com.android.tools.idea.streaming.uisettings.data.AppLanguage
import com.android.tools.idea.streaming.uisettings.stats.UiSettingsStats
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsController
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.launch

/**
 * A controller for the UI settings for a physical device,
 * that populates the model and reacts to changes to the model initiated by the UI.
 */
internal class DeviceUiSettingsController(
  private val deviceController: DeviceController,
  deviceConfig: DeviceConfiguration,
  private val project: Project,
  model: UiSettingsModel,
  parentDisposable: Disposable
) : UiSettingsController(model, UiSettingsStats(deviceConfig.deviceProperties.deviceInfoProto)) {

  private val scope = parentDisposable.createCoroutineScope()

  override suspend fun populateModel() {
    populateModel(deviceController.getUiSettings())
  }

  private fun populateModel(response: UiSettingsResponse) {
    model.inDarkMode.setFromController(response.darkMode)
    model.fontScaleInPercent.setFromController(response.fontScale)
    model.screenDensity.setFromController(response.density)
    model.talkBackOn.setFromController(response.talkBackOn)
    model.selectToSpeakOn.setFromController(response.selectToSpeakOn)
    model.gestureNavigation.setFromController(response.gestureNavigation)
    model.debugLayout.setFromController(response.debugLayout)
    AppLanguageService.getInstance(project).getAppLanguageInfo(
      RunningApplicationIdentity(applicationId = response.foregroundApplicationId, processName = null))?.let {
      addLanguage(it.applicationId, it.localeConfig, response.appLocale)
    }
    model.differentFromDefault.setFromController(!response.originalValues)
    model.fontScaleSettable.setFromController(response.fontScaleSettable)
    model.screenDensitySettable.setFromController(response.densitySettable)
    model.talkBackInstalled.setFromController(response.tackBackInstalled)
    model.gestureOverlayInstalled.setFromController(response.gestureOverlayInstalled)
  }

  private fun handleCommandResponse(response: UiSettingsChangeResponse) {
    model.differentFromDefault.setFromController(!response.originalValues)
  }

  override fun setDarkMode(on: Boolean) {
    scope.launch {
      handleCommandResponse(deviceController.setDarkMode(on))
    }
  }

  override fun setFontScale(percent: Int) {
    scope.launch {
      handleCommandResponse(deviceController.setFontScale(percent))
    }
  }

  override fun setScreenDensity(density: Int) {
    scope.launch {
      handleCommandResponse(deviceController.setScreenDensity(density))
    }
  }

  override fun setTalkBack(on: Boolean) {
    scope.launch {
      handleCommandResponse(deviceController.setTalkBack(on))
    }
  }

  override fun setSelectToSpeak(on: Boolean) {
    scope.launch {
      handleCommandResponse(deviceController.setSelectToSpeak(on))
    }
  }

  override fun setGestureNavigation(on: Boolean) {
    scope.launch {
      handleCommandResponse(deviceController.setGestureNavigation(on))
    }
  }

  override fun setDebugLayout(on: Boolean) {
    scope.launch {
      handleCommandResponse(deviceController.setDebugLayout(on))
    }
  }

  override fun setAppLanguage(applicationId: String, language: AppLanguage?) {
    scope.launch {
      handleCommandResponse(deviceController.setAppLanguage(applicationId, language?.tag ?: ""))
    }
  }

  override fun reset() {
    scope.launch {
      populateModel(deviceController.resetUiSettings())
    }
  }
}
