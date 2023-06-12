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
package com.android.tools.idea.streaming.emulator

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.idea.streaming.AbstractDisplayPanel
import com.android.tools.idea.streaming.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.PRIMARY_DISPLAY_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import java.awt.Dimension

/**
 * Represents a single Emulator display.
 */
class EmulatorDisplayPanel(
  disposableParent: Disposable,
  emulator: EmulatorController,
  displayId: Int,
  displaySize: Dimension?,
  zoomToolbarVisible: Boolean,
  deviceFrameVisible: Boolean = false,
) : AbstractDisplayPanel<EmulatorView>(disposableParent, zoomToolbarVisible), DataProvider {

  init {
    displayView = EmulatorView(this, emulator, displayId, displaySize, deviceFrameVisible)

    if (displayId == PRIMARY_DISPLAY_ID) {
      loadingPanel.setLoadingText("Connecting to the Emulator")
      loadingPanel.startLoading() // The stopLoading method is called by EmulatorView after the gRPC connection is established.
    }
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_CONTROLLER_KEY.name -> displayView.emulator
      EMULATOR_VIEW_KEY.name, DISPLAY_VIEW_KEY.name, ZOOMABLE_KEY.name -> displayView
      else -> null
    }
  }
}
