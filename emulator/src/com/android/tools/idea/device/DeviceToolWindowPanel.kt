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

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.util.ActionToolbarUtil.makeToolbarNavigable
import com.android.tools.idea.emulator.AbstractDisplayPanel
import com.android.tools.idea.emulator.DeviceId
import com.android.tools.idea.emulator.RunningDevicePanel
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
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent
import javax.swing.SwingConstants

/**
 * Provides view of one physical device in the Running Devices tool window.
 */
internal class DeviceToolWindowPanel(
  private val project: Project,
  private val deviceSerialNumber: String,
  private val deviceAbi: String,
  override val title: String
) : RunningDevicePanel(DeviceId.ofPhysicalDevice(deviceSerialNumber)) {

  private val mainToolbar: ActionToolbar
  private val centerPanel = BorderLayoutPanel()
  private var displayPanel: DeviceDisplayPanel? = null
  private var contentDisposable: Disposable? = null

  private var primaryDeviceView: DeviceView? = null

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

  @get:TestOnly
  var lastUiState: PhysicalDeviceUiState? = null

  init {
    background = primaryPanelBackground

    mainToolbar = createToolbar(DEVICE_MAIN_TOOLBAR_ID, isToolbarHorizontal)

    addToCenter(centerPanel)

    if (isToolbarHorizontal) {
      mainToolbar.setOrientation(SwingConstants.HORIZONTAL)
      centerPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      addToTop(mainToolbar.component)
    }
    else {
      mainToolbar.setOrientation(SwingConstants.VERTICAL)
      centerPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.LEFT)
      addToLeft(mainToolbar.component)
    }
  }

  override fun setDeviceFrameVisible(visible: Boolean) {
    // Showing device frame is not supported for physical devices.
  }

  /**
   * Populates the device panel with content.
   */
  override fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState?) {
    lastUiState = null
    val disposable = Disposer.newDisposable()
    contentDisposable = disposable

    val primaryDisplayPanel = DeviceDisplayPanel(disposable, deviceSerialNumber, deviceAbi, project, zoomToolbarVisible)
    displayPanel = primaryDisplayPanel
    val deviceView = primaryDisplayPanel.displayView
    primaryDeviceView = deviceView
    mainToolbar.setTargetComponent(deviceView)
    centerPanel.addToCenter(primaryDisplayPanel)
  }

  /**
   * Destroys content of the device panel and returns its state for later recreation.
   */
  override fun destroyContent(): UiState {
    val uiState = PhysicalDeviceUiState()
    uiState.zoomScrollState = displayPanel?.zoomScrollState

    contentDisposable?.let { Disposer.dispose(it) }
    contentDisposable = null

    centerPanel.removeAll()
    displayPanel = null
    primaryDeviceView = null
    mainToolbar.setTargetComponent(this)
    lastUiState = uiState
    return uiState
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      DEVICE_VIEW_KEY.name, ZOOMABLE_KEY.name -> primaryDeviceView
      DEVICE_CONTROLLER_KEY.name -> primaryDeviceView?.deviceController
      else -> null
    }
  }

  @Suppress("SameParameterValue")
  private fun createToolbar(toolbarId: String, horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    toolbar.layoutPolicy = ActionToolbar.AUTO_LAYOUT_POLICY
    toolbar.setTargetComponent(this)
    makeToolbarNavigable(toolbar)
    return toolbar
  }

  class PhysicalDeviceUiState : UiState {
    var zoomScrollState: AbstractDisplayPanel.ZoomScrollState? = null
  }
}

private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.Avd.DEVICE_PHONE)
private const val isToolbarHorizontal = true