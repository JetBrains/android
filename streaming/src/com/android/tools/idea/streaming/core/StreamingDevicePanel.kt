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

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.ui.NotificationHolderPanel
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.EventQueue
import java.awt.Point
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

private const val IS_TOOLBAR_HORIZONTAL = true

/**
 * Provides view of one Android device in the Running Devices tool window.
 */
abstract class StreamingDevicePanel(
  val id: DeviceId,
  mainToolbarId: String,
  secondaryToolbarId: String,
) : BorderLayoutPanel(), UiDataProvider, Disposable {

  /** Plain text name of the device. */
  internal abstract val title: String
  /** An HTML string containing detailed information about the device. */
  internal abstract val description: String
  internal abstract val icon: Icon
  internal abstract val deviceType: DeviceType
  internal abstract val preferredFocusableComponent: JComponent

  internal abstract var zoomToolbarVisible: Boolean
  internal abstract val primaryDisplayView: AbstractDisplayView?
  internal val hasContent: Boolean
    get() = primaryDisplayView != null

  protected val mainToolbar: ActionToolbar
  protected val secondaryToolbar: ActionToolbar
  protected val centerPanel = BorderLayoutPanel()

  init {
    background = primaryPanelBackground
    val layoutStrategy =
      if (StudioFlags.RUNNING_DEVICES_WRAP_TOOLBAR.get()) ToolbarLayoutStrategy.WRAP_STRATEGY else ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
    mainToolbar = createToolbar(mainToolbarId, layoutStrategy, IS_TOOLBAR_HORIZONTAL)
    secondaryToolbar = createToolbar(secondaryToolbarId, ToolbarLayoutStrategy.NOWRAP_STRATEGY, IS_TOOLBAR_HORIZONTAL)
    secondaryToolbar.isReservePlaceAutoPopupIcon = false

    addToCenter(centerPanel)

    val toolbarPanel = BorderLayoutPanel()
    if (IS_TOOLBAR_HORIZONTAL) {
      toolbarPanel.add(mainToolbar.component, BorderLayout.CENTER)
      toolbarPanel.add(secondaryToolbar.component, BorderLayout.EAST)
      toolbarPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.BOTTOM)
      addToTop(toolbarPanel)
    }
    else {
      toolbarPanel.add(mainToolbar.component, BorderLayout.CENTER)
      toolbarPanel.add(secondaryToolbar.component, BorderLayout.SOUTH)
      toolbarPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.RIGHT)
      addToLeft(toolbarPanel)
    }
  }

  /**
   * Adds a notification panel. If the [notificationPanel] has a close action, that action has to make
   * sure that the notification is removed when the action is executed.
   */
  fun addNotification(notificationPanel: EditorNotificationPanel) {
    findNotificationHolderPanel()?.addNotification(notificationPanel)
  }

  /** Removes the given notification panel. */
  fun removeNotification(notificationPanel: EditorNotificationPanel) {
    findNotificationHolderPanel()?.removeNotification(notificationPanel)
  }

  internal abstract fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState? = null)
  internal abstract fun destroyContent(): UiState
  internal abstract fun setDeviceFrameVisible(visible: Boolean)

  override fun uiDataSnapshot(sink: DataSink) {
    sink[DISPLAY_VIEW_KEY] = primaryDisplayView
    sink[ZOOMABLE_KEY] = primaryDisplayView
    sink[SERIAL_NUMBER_KEY] = id.serialNumber
    sink[STREAMING_CONTENT_PANEL_KEY] = centerPanel
    sink[DEVICE_ID_KEY] = id
  }

  override fun dispose() {
    destroyContent()
  }

  /**
   * Shows a context menu advertisement if the context menu is enabled and the advertisement has not been shown yet.
   */
  protected fun showContextMenuAdvertisementIfNecessary(disposableParent: Disposable) {
    if (StudioFlags.RUNNING_DEVICES_CONTEXT_MENU.get() && !EmulatorSettings.getInstance().contextMenuAdvertisementShown) {
      EventQueue.invokeLater {
        if (hasContent) {
          showContextMenuAdvertisement(disposableParent, mainToolbar.component)
        }
      }
    }
  }

  /**
   * Shows a balloon advertising moving of some toolbar actions into the context menu.
   * The balloon is shown below the main toolbar.
   */
  private fun showContextMenuAdvertisement(disposableParent: Disposable, toolbarComponent: JComponent) {
    val disposable = Disposer.newDisposable(disposableParent)
    val advertisementCloser = StreamingContextMenuAdvertisementCloser {
      Disposer.dispose(disposable)
      EmulatorSettings.getInstance().contextMenuAdvertisementShown = true
    }

    val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(JLabel("Some toolbar buttons have been moved to a context menu. Use right-click to access."))
      .setDisposable(disposable)
      .setClickHandler({ advertisementCloser.closeContextMenuAdvertisement() }, true)
      .setShadow(true)
      .setHideOnAction(false)
      .setHideOnClickOutside(false)
      .setBlockClicksThroughBalloon(true)
      //.setAnimationCycle(200)
      .setFillColor(HintUtil.getWarningColor())
      .setBorderColor(HintUtil.getWarningColor())
      .createBalloon()

    val positionTracker = object : PositionTracker<Balloon>(toolbarComponent), ComponentListener, ContainerListener {
      private var lastToolbarButton: Component? = null

      init {
        toolbarComponent.addContainerListener(this)
        Disposer.register(this) {
          lastToolbarButton?.removeComponentListener(this)
          toolbarComponent.removeContainerListener(this)
        }
      }

      override fun recalculateLocation(balloon: Balloon): RelativePoint? {
        val button = toolbarComponent.components.findLast { it.isVisible && it.width != 0 }
        if (button !== lastToolbarButton) {
          lastToolbarButton?.removeComponentListener(this)
          button?.addComponentListener(this)
          lastToolbarButton = button
        }
        val p = button?.let { Point(it.x + it.width, it.y + it.height) } ?: Point()
        return RelativePoint(component, p)
      }

      override fun componentResized(e: ComponentEvent) {
        revalidate()
      }

      override fun componentMoved(e: ComponentEvent) {
        revalidate()
      }

      override fun componentShown(e: ComponentEvent) {
        revalidate()
      }

      override fun componentHidden(e: ComponentEvent) {
        revalidate()
      }

      override fun componentAdded(event: ContainerEvent) {
        revalidate()
      }

      override fun componentRemoved(event: ContainerEvent) {
        revalidate()
      }
    }

    balloon.show(positionTracker, Balloon.Position.below)

    val messageBusConnection = ApplicationManager.getApplication().messageBus.connect(disposable)
    messageBusConnection.subscribe(StreamingContextMenuAdvertisementCloser.TOPIC, advertisementCloser)
  }

  @Suppress("SameParameterValue")
  private fun createToolbar(toolbarId: String, strategy: ToolbarLayoutStrategy, horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    toolbar.layoutStrategy = strategy
    toolbar.setLayoutSecondaryActions(true)
    toolbar.targetComponent = this
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    return toolbar
  }

  private fun findNotificationHolderPanel() =
      primaryDisplayView?.findContainingComponent<NotificationHolderPanel>()

  internal interface UiState
}