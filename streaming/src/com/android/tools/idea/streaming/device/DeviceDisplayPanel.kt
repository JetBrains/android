/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.streaming.core.AbstractDisplayPanel
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project

/**
 * Represents a single display of an Android device.
 */
internal class DeviceDisplayPanel(
  disposableParent: Disposable,
  deviceClient: DeviceClient,
  displayId: Int,
  initialDisplayOrientation: Int,
  project: Project,
  zoomToolbarVisible: Boolean,
) : AbstractDisplayPanel<DeviceView>(disposableParent, zoomToolbarVisible), UiDataProvider {

  init {
    displayView = DeviceView(this, deviceClient, displayId, initialDisplayOrientation, project)

    loadingPanel.setLoadingText("Connecting to the device")
    loadingPanel.startLoading() // The stopLoading method is called by DeviceView after a connection to the device is established.
  }

  override fun uiDataSnapshot(sink: DataSink) {
      sink[DEVICE_CLIENT_KEY] = displayView.deviceClient
      sink[DEVICE_CONTROLLER_KEY] = displayView.deviceController
      sink[DEVICE_VIEW_KEY] = displayView
      sink[DISPLAY_VIEW_KEY] = displayView
      sink[ZOOMABLE_KEY] = displayView
      sink[SERIAL_NUMBER_KEY] = displayView.deviceClient.deviceSerialNumber
  }
}
