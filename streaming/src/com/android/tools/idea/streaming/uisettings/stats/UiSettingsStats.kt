/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.uisettings.stats

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.UiDeviceSettingsEvent
import com.google.wireless.android.sdk.stats.UiDeviceSettingsEvent.OperationKind

/**
 * Analytics logger for various events from the UI settings panel.
 */
class UiSettingsStats(private val deviceInfo: DeviceInfo?) {

  fun setDarkMode() = logUiSettingsChange(OperationKind.DARK_THEME)

  fun setGestureNavigation() = logUiSettingsChange(OperationKind.GESTURE_NAVIGATION)

  fun setAppLanguage() = logUiSettingsChange(OperationKind.APP_LANGUAGE)

  fun setTalkBack() = logUiSettingsChange(OperationKind.TALKBACK)

  fun setSelectToSpeak() = logUiSettingsChange(OperationKind.SELECT_TO_SPEAK)

  fun setFontScale() = logUiSettingsChange(OperationKind.FONT_SIZE)

  fun setScreenDensity() = logUiSettingsChange(OperationKind.SCREEN_DENSITY)

  fun setDebugLayout() = logUiSettingsChange(OperationKind.DEBUG_LAYOUT)

  fun reset() = logUiSettingsChange(OperationKind.RESET)

  private fun logUiSettingsChange(operation: OperationKind) {
    val studioEvent = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.UI_DEVICE_SETTINGS_EVENT)
      .setUiDeviceSettingsEvent(UiDeviceSettingsEvent.newBuilder().setOperation(operation))
    if (deviceInfo != null) {
      studioEvent.setDeviceInfo(deviceInfo)
    }
    UsageTracker.log(studioEvent)
  }
}
