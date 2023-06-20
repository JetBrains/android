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
import com.android.tools.idea.streaming.AbstractDisplayPanel
import com.android.tools.idea.streaming.DISPLAY_VIEW_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project

/**
 * Represents a single display of an Android device.
 */
internal class DeviceDisplayPanel(
  disposableParent: Disposable,
  deviceClient: DeviceClient,
  initialDisplayOrientation: Int,
  project: Project,
  zoomToolbarVisible: Boolean,
) : AbstractDisplayPanel<DeviceView>(disposableParent, zoomToolbarVisible), DataProvider {

  init {
    displayView = DeviceView(this, deviceClient, initialDisplayOrientation, project)

    loadingPanel.setLoadingText("Connecting to the device")
    loadingPanel.startLoading() // The stopLoading method is called by DeviceView after a connection to the device is established.
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      DEVICE_VIEW_KEY.name, DISPLAY_VIEW_KEY.name, ZOOMABLE_KEY.name -> displayView
      else -> null
    }
  }
}
