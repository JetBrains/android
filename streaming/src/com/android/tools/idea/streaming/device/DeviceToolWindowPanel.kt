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

import com.android.annotations.concurrency.AnyThread
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.idea.streaming.AbstractDisplayPanel
import com.android.tools.idea.streaming.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.DeviceId
import com.android.tools.idea.streaming.RunningDevicePanel
import com.android.tools.idea.streaming.STREAMING_SECONDARY_TOOLBAR_ID
import com.android.tools.idea.streaming.device.DeviceView.ConnectionState
import com.android.tools.idea.streaming.device.DeviceView.ConnectionStateListener
import com.android.tools.idea.streaming.device.screenshot.DeviceScreenshotOptions
import com.android.tools.idea.streaming.installFileDropHandler
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction
import com.android.tools.idea.ui.screenshot.ScreenshotAction
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DeviceMirroringSession
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import icons.StudioIcons
import java.awt.EventQueue
import javax.swing.JComponent

private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.Avd.DEVICE_PHONE)

/**
 * Provides view of one physical device in the Running Devices tool window.
 */
internal class DeviceToolWindowPanel(
  private val project: Project,
  private val deviceClient: DeviceClient,
) : RunningDevicePanel(DeviceId.ofPhysicalDevice(deviceClient.deviceSerialNumber), DEVICE_MAIN_TOOLBAR_ID, STREAMING_SECONDARY_TOOLBAR_ID) {

  private var displayPanel: DeviceDisplayPanel? = null
  private var contentDisposable: Disposable? = null
  private var primaryDeviceView: DeviceView? = null
  private val deviceSerialNumber: String
    get() = deviceClient.deviceSerialNumber
  private val deviceConfig
    get() = deviceClient.deviceConfig

  override val title: String
    get() = deviceClient.deviceName

  override val icon
    get() = ICON

  override val isClosable = false

  val component: JComponent
    get() = this

  override val preferredFocusableComponent: JComponent
    get() = primaryDeviceView ?: this

  override var zoomToolbarVisible = false
    set(value) {
      field = value
      displayPanel?.zoomToolbarVisible = value
    }

  override fun setDeviceFrameVisible(visible: Boolean) {
    // Showing device frame is not supported for physical devices.
  }

  override fun getDeviceInfo(): DeviceInfo {
    return DeviceInfo.newBuilder()
        .fillFrom(deviceConfig)
        .fillMdnsConnectionType(deviceSerialNumber)
        .build()
  }

  /**
   * Populates the device panel with content.
   */
  override fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState?) {
    mirroringStarted()

    val disposable = Disposer.newDisposable()
    contentDisposable = disposable

    savedUiState as DeviceUiState?
    val initialOrientation = savedUiState?.orientation ?: UNKNOWN_ORIENTATION
    val primaryDisplayPanel = DeviceDisplayPanel(disposable, deviceClient, initialOrientation, project, zoomToolbarVisible)
    savedUiState?.zoomScrollState?.let { primaryDisplayPanel.zoomScrollState = it }

    displayPanel = primaryDisplayPanel
    val deviceView = primaryDisplayPanel.displayView
    primaryDeviceView = deviceView
    mainToolbar.targetComponent = deviceView
    secondaryToolbar.targetComponent = deviceView
    centerPanel.addToCenter(primaryDisplayPanel)
    deviceView.addConnectionStateListener(object : ConnectionStateListener {
      @AnyThread
      override fun connectionStateChanged(deviceSerialNumber: String, connectionState: ConnectionState) {
        EventQueue.invokeLater {
          mainToolbar.updateActionsImmediately()
          secondaryToolbar.updateActionsImmediately()
        }
      }
    })

    installFileDropHandler(this, id.serialNumber, deviceView, project)
  }

  /**
   * Destroys content of the device panel and returns its state for later recreation.
   */
  override fun destroyContent(): DeviceUiState {
    mirroringEnded(DeviceMirroringSession.DeviceKind.PHYSICAL)

    val uiState = DeviceUiState()
    uiState.orientation = primaryDeviceView?.displayOrientationQuadrants ?: 0
    uiState.zoomScrollState = displayPanel?.zoomScrollState

    contentDisposable?.let { Disposer.dispose(it) }
    contentDisposable = null

    centerPanel.removeAll()
    displayPanel = null
    primaryDeviceView = null
    mainToolbar.targetComponent = this
    secondaryToolbar.targetComponent = this
    return uiState
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      DEVICE_VIEW_KEY.name, DISPLAY_VIEW_KEY.name, ZOOMABLE_KEY.name -> primaryDeviceView
      DEVICE_CLIENT_KEY.name -> deviceClient
      DEVICE_CONTROLLER_KEY.name -> deviceClient.deviceController
      ScreenshotAction.SCREENSHOT_OPTIONS_KEY.name ->
          primaryDeviceView?.let { if (it.isConnected) DeviceScreenshotOptions(deviceSerialNumber, deviceConfig, it) else null }
      ScreenRecorderAction.SCREEN_RECORDER_PARAMETERS_KEY.name ->
          deviceClient.deviceController?.let {
            ScreenRecorderAction.Parameters(deviceClient.deviceName, deviceSerialNumber, deviceConfig.featureLevel, null, it)
          }
      else -> super.getData(dataId)
    }
  }

  class DeviceUiState : UiState {
    var orientation = 0
    var zoomScrollState: AbstractDisplayPanel.ZoomScrollState? = null
  }
}
