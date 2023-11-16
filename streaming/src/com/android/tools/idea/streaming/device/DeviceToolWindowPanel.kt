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
import com.android.tools.idea.deviceprovisioner.DEVICE_HANDLE_KEY
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.core.AbstractDisplayPanel
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.RunningDevicePanel
import com.android.tools.idea.streaming.core.STREAMING_SECONDARY_TOOLBAR_ID
import com.android.tools.idea.streaming.core.htmlColored
import com.android.tools.idea.streaming.core.installFileDropHandler
import com.android.tools.idea.streaming.device.DeviceView.ConnectionState
import com.android.tools.idea.streaming.device.DeviceView.ConnectionStateListener
import com.android.tools.idea.streaming.device.screenshot.DeviceScreenshotOptions
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction
import com.android.tools.idea.ui.screenshot.ScreenshotAction
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DeviceMirroringSession
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import java.awt.EventQueue
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Provides view of one physical device in the Running Devices tool window.
 */
internal class DeviceToolWindowPanel(
  private val project: Project,
  val deviceClient: DeviceClient,
) : RunningDevicePanel(DeviceId.ofPhysicalDevice(deviceClient.deviceSerialNumber), DEVICE_MAIN_TOOLBAR_ID, STREAMING_SECONDARY_TOOLBAR_ID) {

  val deviceSerialNumber: String
    get() = deviceClient.deviceSerialNumber

  override val title: String
    get() = deviceClient.deviceName

  override val description: String
    get() {
      val properties = deviceClient.deviceHandle.state.properties
      val api = properties.androidVersion?.apiStringWithoutExtension ?: "${deviceClient.deviceConfig.apiLevel}"
      return "${properties.title} API $api ${"($deviceSerialNumber)".htmlColored(JBColor.GRAY)}"
    }

  override val icon: Icon
    get() = ExecutionUtil.getLiveIndicator(deviceClient.deviceHandle.state.properties.icon)

  override val isClosable: Boolean = StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.get()

  val component: JComponent
    get() = this

  override val preferredFocusableComponent: JComponent
    get() = primaryDisplayView ?: this

  override var zoomToolbarVisible = false
    set(value) {
      field = value
      displayPanel?.zoomToolbarVisible = value
    }

  private var displayPanel: DeviceDisplayPanel? = null
  private var contentDisposable: Disposable? = null
  override var primaryDisplayView: DeviceView? = null
    private set
  private val deviceConfig
    get() = deviceClient.deviceConfig
  private val deviceStateListener = object : DeviceController.DeviceStateListener {
    override fun onSupportedDeviceStatesChanged(deviceStates: List<FoldingState>) {
      updateMainToolbarLater()
    }

    override fun onDeviceStateChanged(deviceState: Int) {
      updateMainToolbarLater()
    }

    private fun updateMainToolbarLater() {
      EventQueue.invokeLater {
        mainToolbar.updateActionsImmediately()
      }
    }
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

    val uiState = savedUiState as DeviceUiState? ?: DeviceUiState()
    val initialOrientation = uiState.orientation
    val primaryDisplayPanel = DeviceDisplayPanel(disposable, deviceClient, initialOrientation, project, zoomToolbarVisible)
    uiState.zoomScrollState?.let { primaryDisplayPanel.zoomScrollState = it }

    displayPanel = primaryDisplayPanel
    val deviceView = primaryDisplayPanel.displayView
    primaryDisplayView = deviceView
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
        when (connectionState) {
          ConnectionState.CONNECTED -> deviceClient.deviceController?.addDeviceStateListener(deviceStateListener)
          ConnectionState.DISCONNECTED -> deviceClient.deviceController?.removeDeviceStateListener(deviceStateListener)
          else -> {}
        }
      }
    })

    installFileDropHandler(this, id.serialNumber, deviceView, project)

    restoreActiveNotifications(uiState)
  }

  /**
   * Destroys content of the device panel and returns its state for later recreation.
   */
  override fun destroyContent(): DeviceUiState {
    mirroringEnded(DeviceMirroringSession.DeviceKind.PHYSICAL)

    val uiState = DeviceUiState()
    saveActiveNotifications(uiState)
    uiState.orientation = primaryDisplayView?.displayOrientationQuadrants ?: 0
    uiState.zoomScrollState = displayPanel?.zoomScrollState

    contentDisposable?.let { Disposer.dispose(it) }
    contentDisposable = null

    centerPanel.removeAll()
    displayPanel = null
    primaryDisplayView = null
    mainToolbar.targetComponent = this
    secondaryToolbar.targetComponent = this
    return uiState
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      DEVICE_VIEW_KEY.name -> primaryDisplayView
      DEVICE_CLIENT_KEY.name -> deviceClient
      DEVICE_CONTROLLER_KEY.name -> deviceClient.deviceController
      DEVICE_HANDLE_KEY.name -> deviceClient.deviceHandle
      ScreenshotAction.SCREENSHOT_OPTIONS_KEY.name ->
          primaryDisplayView?.let { if (it.isConnected) DeviceScreenshotOptions(deviceSerialNumber, deviceConfig, it) else null }
      ScreenRecorderAction.SCREEN_RECORDER_PARAMETERS_KEY.name ->
          deviceClient.deviceController?.let {
            ScreenRecorderAction.Parameters(deviceClient.deviceName, deviceSerialNumber, deviceConfig.featureLevel, null, it)
          }
      else -> super.getData(dataId)
    }
  }

  class DeviceUiState : UiState() {
    var orientation = UNKNOWN_ORIENTATION
    var zoomScrollState: AbstractDisplayPanel.ZoomScrollState? = null
  }
}
