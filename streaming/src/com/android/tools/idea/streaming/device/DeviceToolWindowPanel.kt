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
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.util.ActionToolbarUtil.makeToolbarNavigable
import com.android.tools.idea.streaming.AbstractDisplayPanel
import com.android.tools.idea.streaming.DeviceId
import com.android.tools.idea.streaming.RunningDevicePanel
import com.android.tools.idea.streaming.device.DeviceView.ConnectionState
import com.android.tools.idea.streaming.device.DeviceView.ConnectionStateListener
import com.android.tools.idea.streaming.device.screenshot.DeviceScreenshotOptions
import com.android.tools.idea.streaming.emulator.EMULATOR_SECONDARY_TOOLBAR_ID
import com.android.tools.idea.streaming.installFileDropHandler
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction
import com.android.tools.idea.ui.screenshot.ScreenshotAction
import com.google.wireless.android.sdk.stats.DeviceMirroringSession
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.EventQueue
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

/**
 * Provides view of one physical device in the Running Devices tool window.
 */
internal class DeviceToolWindowPanel(
  private val project: Project,
  private val deviceSerialNumber: String,
  private val deviceAbi: String,
  override val title: String,
  deviceProperties: Map<String, String>,
) : RunningDevicePanel(DeviceId.ofPhysicalDevice(deviceSerialNumber)) {

  private val toolbarPanel = BorderLayoutPanel()
  private val mainToolbar: ActionToolbar
  private val secondaryToolbar: ActionToolbar
  private val centerPanel = BorderLayoutPanel()
  private var displayPanel: DeviceDisplayPanel? = null
  private var contentDisposable: Disposable? = null

  private var primaryDeviceView: DeviceView? = null

  override val icon
    get() = ICON

  override val isClosable = false

  val component: JComponent
    get() = this

  private val deviceConfiguration = DeviceConfiguration(deviceProperties)
  private val apiLevel
    get() = deviceConfiguration.apiLevel

  private val avdName
    get() = deviceConfiguration.avdName

  override val preferredFocusableComponent: JComponent
    get() = primaryDeviceView ?: this

  override var zoomToolbarVisible = false
    set(value) {
      field = value
      displayPanel?.zoomToolbarVisible = value
    }

  init {
    background = primaryPanelBackground

    mainToolbar = createToolbar(DEVICE_MAIN_TOOLBAR_ID, isToolbarHorizontal)
    secondaryToolbar = createToolbar(EMULATOR_SECONDARY_TOOLBAR_ID, isToolbarHorizontal)

    addToCenter(centerPanel)

    if (isToolbarHorizontal) {
      mainToolbar.setOrientation(SwingConstants.HORIZONTAL)
      secondaryToolbar.setOrientation(SwingConstants.HORIZONTAL)
      toolbarPanel.add(mainToolbar.component, BorderLayout.CENTER)
      toolbarPanel.add(secondaryToolbar.component, BorderLayout.EAST)
      centerPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      addToTop(toolbarPanel)
    }
    else {
      mainToolbar.setOrientation(SwingConstants.VERTICAL)
      secondaryToolbar.setOrientation(SwingConstants.VERTICAL)
      toolbarPanel.add(mainToolbar.component, BorderLayout.CENTER)
      toolbarPanel.add(secondaryToolbar.component, BorderLayout.SOUTH)
      centerPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.LEFT)
      addToLeft(toolbarPanel)
    }
  }

  override fun setDeviceFrameVisible(visible: Boolean) {
    // Showing device frame is not supported for physical devices.
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
    val primaryDisplayPanel =
        DeviceDisplayPanel(disposable, deviceSerialNumber, deviceAbi, title, initialOrientation, project, zoomToolbarVisible)
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
      DEVICE_VIEW_KEY.name, ZOOMABLE_KEY.name -> primaryDeviceView
      DEVICE_CONTROLLER_KEY.name -> primaryDeviceView?.deviceController
      DEVICE_CONFIGURATION_KEY.name -> deviceConfiguration
      ScreenshotAction.SCREENSHOT_OPTIONS_KEY.name ->
          primaryDeviceView?.let { if (it.isConnected) DeviceScreenshotOptions(deviceSerialNumber, deviceConfiguration, it) else null }
      ScreenRecorderAction.SCREEN_RECORDER_PARAMETERS_KEY.name ->
          primaryDeviceView?.let {
            ScreenRecorderAction.Parameters(deviceSerialNumber, deviceConfiguration.apiLevel, deviceConfiguration.avdName, it)
          }
      else -> super.getData(dataId)
    }
  }

  @Suppress("SameParameterValue")
  private fun createToolbar(toolbarId: String, horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    toolbar.layoutPolicy = ActionToolbar.AUTO_LAYOUT_POLICY
    toolbar.targetComponent = this
    makeToolbarNavigable(toolbar)
    return toolbar
  }

  class DeviceUiState : UiState {
    var orientation = 0
    var zoomScrollState: AbstractDisplayPanel.ZoomScrollState? = null
  }
}

private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.Avd.DEVICE_PHONE)
private const val isToolbarHorizontal = true