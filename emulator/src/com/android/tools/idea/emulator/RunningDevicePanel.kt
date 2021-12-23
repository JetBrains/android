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

  abstract fun setDeviceFrameVisible(visible: Boolean)
  abstract fun destroyContent(): UiState
  abstract fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState? = null)

  interface UiState
}