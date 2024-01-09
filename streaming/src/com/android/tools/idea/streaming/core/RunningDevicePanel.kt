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
package com.android.tools.idea.streaming.core

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.ui.NotificationHolderPanel
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DeviceMirroringSession
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.ui.EditorNotificationPanel
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
  secondaryToolbarId: String,
) : BorderLayoutPanel(), DataProvider {

  /** Plain text name of the device. */
  internal abstract val title: String
  /** An HTML string containing detailed information about the device. */
  internal abstract val description: String
  internal abstract val icon: Icon
  internal abstract val isClosable: Boolean
  internal abstract val preferredFocusableComponent: JComponent

  internal abstract var zoomToolbarVisible: Boolean
  internal abstract val primaryDisplayView: AbstractDisplayView?

  protected val mainToolbar: ActionToolbar
  protected val secondaryToolbar: ActionToolbar
  protected val centerPanel = BorderLayoutPanel()
  private val pendingNotifications = mutableListOf<EditorNotificationPanel>()

  // Start time of the current device mirroring session in milliseconds since epoch.
  private var mirroringStartTime: Long = 0

  init {
    background = primaryPanelBackground

    mainToolbar = createToolbar(mainToolbarId, IS_TOOLBAR_HORIZONTAL)
    secondaryToolbar = createToolbar(secondaryToolbarId, IS_TOOLBAR_HORIZONTAL)
    secondaryToolbar.setReservePlaceAutoPopupIcon(false)

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

  /** Adds a notification panel that is removed when its close icon is clicked. */
  fun addNotification(notificationPanel: EditorNotificationPanel) {
    notificationPanel.setCloseAction { removeNotification(notificationPanel) }
    findNotificationHolderPanel()?.addNotification(notificationPanel) ?: pendingNotifications.add(notificationPanel)
  }

  /** Removes the given notification panel. */
  fun removeNotification(notificationPanel: EditorNotificationPanel) {
    findNotificationHolderPanel()?.removeNotification(notificationPanel) ?: pendingNotifications.remove(notificationPanel)
  }

  internal abstract fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState? = null)
  internal abstract fun destroyContent(): UiState
  internal abstract fun setDeviceFrameVisible(visible: Boolean)
  /** Returns device information for metrics collection. */
  protected abstract fun getDeviceInfo(): DeviceInfo

  internal fun saveActiveNotifications(uiState: UiState) {
    findNotificationHolderPanel()?.let { uiState.activeNotifications.addAll(it.notificationPanels) }
    uiState.activeNotifications.addAll(pendingNotifications)
    pendingNotifications.clear()
  }

  internal fun restoreActiveNotifications(uiState: UiState) {
    val activeNotifications = uiState.activeNotifications
    if (activeNotifications.isNotEmpty() || pendingNotifications.isNotEmpty()) {
      val notificationHolderPanel = findNotificationHolderPanel()
      if (notificationHolderPanel != null) {
        activeNotifications.forEach { notificationHolderPanel.addNotification(it) }
        pendingNotifications.forEach { notificationHolderPanel.addNotification(it) }
        pendingNotifications.clear()
      }
    }
  }

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
    if (mirroringStartTime == 0L) {
      return
    }
    val durationSec = (System.currentTimeMillis() - mirroringStartTime) / 1000
    mirroringStartTime = 0

    val studioEvent = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DEVICE_MIRRORING_SESSION)
      .setDeviceMirroringSession(
        DeviceMirroringSession.newBuilder()
          .setDeviceKind(deviceKind)
          .setDurationSec(durationSec)
      )
      .setDeviceInfo(getDeviceInfo())

    UsageTracker.log(studioEvent)
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      DISPLAY_VIEW_KEY.name, ZOOMABLE_KEY.name -> primaryDisplayView
      SERIAL_NUMBER_KEY.name -> id.serialNumber
      STREAMING_CONTENT_PANEL_KEY.name -> centerPanel
      DEVICE_ID_KEY.name -> id
      else -> null
    }
  }

  @Suppress("SameParameterValue")
  private fun createToolbar(toolbarId: String, horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    toolbar.layoutStrategy = if (horizontal) ToolbarLayoutStrategy.HORIZONTAL_AUTOLAYOUT_STRATEGY else ToolbarLayoutStrategy.VERTICAL_AUTOLAYOUT_STRATEGY
    toolbar.setLayoutSecondaryActions(true)
    toolbar.targetComponent = this
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    return toolbar
  }

  private fun findNotificationHolderPanel() =
      primaryDisplayView?.findContainingComponent<NotificationHolderPanel>()

  internal abstract class UiState {
    val activeNotifications: MutableList<EditorNotificationPanel> = mutableListOf()
  }
}