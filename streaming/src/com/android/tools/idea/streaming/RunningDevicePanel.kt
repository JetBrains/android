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
package com.android.tools.idea.streaming

import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceMirroringSession
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingConstants

private const val IS_TOOLBAR_HORIZONTAL = true

/**
 * Provides view of one Android device in the Running Devices tool window.
 */
abstract class RunningDevicePanel(
  val id: DeviceId,
  mainToolbarId: String,
  secondaryToolbarId: String
) : BorderLayoutPanel(), DataProvider {

  abstract val title: String
  abstract val icon: Icon
  abstract val isClosable: Boolean
  abstract val preferredFocusableComponent: JComponent

  abstract var zoomToolbarVisible: Boolean

  protected val mainToolbar: ActionToolbar
  protected val secondaryToolbar: ActionToolbar
  protected val centerPanel = BorderLayoutPanel()

  // Start time of the current device mirroring session in milliseconds since epoch.
  private var mirroringStartTime: Long = 0

  init {
    background = primaryPanelBackground

    mainToolbar = createToolbar(mainToolbarId, IS_TOOLBAR_HORIZONTAL)
    secondaryToolbar = createToolbar(secondaryToolbarId, IS_TOOLBAR_HORIZONTAL)

    addToCenter(centerPanel)

    val toolbarPanel = BorderLayoutPanel()
    if (IS_TOOLBAR_HORIZONTAL) {
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

  abstract fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState? = null)
  abstract fun destroyContent(): UiState
  abstract fun setDeviceFrameVisible(visible: Boolean)

  /**
   * Records the start of a device mirroring session.
   */
  protected fun mirroringStarted() {
    mirroringStartTime = System.currentTimeMillis()
  }

  /**
   * Records the end of a device mirroring session.
   */
  protected fun mirroringEnded(deviceKind: DeviceMirroringSession.DeviceKind) {
    val durationSec = (System.currentTimeMillis() - mirroringStartTime) / 1000
    mirroringStartTime = 0

    val studioEvent = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DEVICE_MIRRORING_SESSION)
      .setDeviceMirroringSession(
        DeviceMirroringSession.newBuilder()
          .setDeviceKind(deviceKind)
          .setDurationSec(durationSec)
      )

    UsageTracker.log(studioEvent)
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      SERIAL_NUMBER_KEY.name -> id.serialNumber
      else -> null
    }
  }

  @Suppress("SameParameterValue")
  private fun createToolbar(toolbarId: String, horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    toolbar.layoutPolicy = ActionToolbar.AUTO_LAYOUT_POLICY
    toolbar.targetComponent = this
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    return toolbar
  }

  interface UiState
}