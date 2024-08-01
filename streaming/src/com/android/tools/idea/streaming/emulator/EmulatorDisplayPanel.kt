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
import com.android.tools.idea.streaming.core.AbstractDisplayPanel
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
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
) : AbstractDisplayPanel<EmulatorView>(disposableParent, zoomToolbarVisible), UiDataProvider {

  init {
    displayView = EmulatorView(this, emulator, displayId, displaySize, deviceFrameVisible)

    if (displayId == PRIMARY_DISPLAY_ID) {
      loadingPanel.setLoadingText("Connecting to the Emulator")
      loadingPanel.startLoading() // The stopLoading method is called by EmulatorView after the gRPC connection is established.
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[EMULATOR_CONTROLLER_KEY] = displayView.emulator
    sink[EMULATOR_VIEW_KEY] = displayView
    sink[DISPLAY_VIEW_KEY] = displayView
    sink[ZOOMABLE_KEY] = displayView
  }
}
