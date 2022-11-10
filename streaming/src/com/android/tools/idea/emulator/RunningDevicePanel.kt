/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceMirroringSession
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Provides view of one Android device in the Running Devices tool window.
 */
abstract class RunningDevicePanel(val id: DeviceId) : BorderLayoutPanel(), DataProvider {

  abstract val title: String
  abstract val icon: Icon
  abstract val isClosable: Boolean
  abstract val preferredFocusableComponent: JComponent

  abstract var zoomToolbarVisible: Boolean

  // Start time of the current device mirroring session in milliseconds since epoch.
  private var mirroringStartTime: Long = 0

  abstract fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState? = null)
  abstract fun destroyContent(): UiState
  abstract fun setDeviceFrameVisible(visible: Boolean)

  /**
   * Records starts of device mirroring.
   */
  protected fun mirroringStarted() {
    mirroringStartTime = System.currentTimeMillis()
  }

  /**
   * Records end of device mirroring.
   */
  protected fun mirroringEnded(deviceKind: DeviceMirroringSession.DeviceKind) {
    val durationSec = (System.currentTimeMillis() - mirroringStartTime) / 1000
    mirroringStartTime = 0

    val studioEvent = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DEVICE_MIRRORING_SESSION)
      .setDeviceMirroringSession(
        DeviceMirroringSession.newBuilder()
          .setDeviceKind(deviceKind)
          .setDurationSec(durationSec)
      )

    UsageTracker.log(studioEvent)
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      SERIAL_NUMBER_KEY.name -> id.serialNumber
      else -> null
    }
  }

  interface UiState
}