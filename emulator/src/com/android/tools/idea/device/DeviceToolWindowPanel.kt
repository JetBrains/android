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
package com.android.tools.idea.device

import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.emulator.DeviceId
import com.android.tools.idea.emulator.RunningDevicePanel
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import icons.StudioIcons
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent

/**
 * Provides view of one physical device in the Running Devices tool window.
 */
internal class DeviceToolWindowPanel(
  private val project: Project,
  private val deviceSerialNumber: String,
  private val deviceAbi: String,
  override val title: String
) : RunningDevicePanel(DeviceId.ofPhysicalDevice(deviceSerialNumber)) {

  override val icon
    get() = ICON

  override val isClosable = false

  val component: JComponent
    get() = this

  override val preferredFocusableComponent: JComponent
    get() = this

  override var zoomToolbarVisible = false

  @get:TestOnly
  var lastUiState: PhysicalDeviceUiState? = null

  init {
    background = primaryPanelBackground
  }

  override fun setDeviceFrameVisible(visible: Boolean) {
    // Showing device frame is not supported for physical devices.
  }

  /**
   * Populates the device panel with content.
   */
  override fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState?) {
    // TODO: Implement.
  }

  /**
   * Destroys content of the device panel and returns its state for later recreation.
   */
  override fun destroyContent(): UiState {
    val uiState = PhysicalDeviceUiState()
    // TODO: Implement.
    return uiState
  }

  override fun getData(dataId: String): Any? {
    return null
  }

  class PhysicalDeviceUiState : UiState
}

private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.Avd.DEVICE_PHONE)
