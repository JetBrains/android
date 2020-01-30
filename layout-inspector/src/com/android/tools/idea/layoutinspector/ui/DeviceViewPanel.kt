/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.PANNABLE_KEY
import com.android.tools.adtui.Pannable
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.ui.AdtUiCursors
import com.android.tools.editor.ActionToolbarUtil
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.legacydevice.CaptureAction
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Cursor
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_SPACE
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.min

private const val MAX_ZOOM = 300
private const val MIN_ZOOM = 10

private const val TOOLBAR_INSET = 14

@TestOnly
const val DEVICE_VIEW_ACTION_TOOLBAR_NAME = "DeviceViewPanel.ActionToolbar"

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel(
  private val layoutInspector: LayoutInspector,
  private val viewSettings: DeviceViewSettings,
  disposableParent: Disposable
) : JPanel(BorderLayout()), Zoomable, DataProvider, Pannable {

  override val scale
    get() = viewSettings.scaleFraction

  override val screenScalingFactor = 1f

  override var isPanning = false
  private var isSpacePressed = false
  private var lastPanMouseLocation: Point? = null
  private var currentZoomOperation: ZoomType? = null

  private val contentPanel = DeviceViewContentPanel(layoutInspector.layoutInspectorModel, viewSettings)

  private val panMouseListener: MouseAdapter = object : MouseAdapter() {
    private fun currentlyPanning(e: MouseEvent) = isPanning || SwingUtilities.isMiddleMouseButton(e) ||
                                                  (SwingUtilities.isLeftMouseButton(e) && isSpacePressed)

    override fun mouseEntered(e: MouseEvent) {
      showGrab()
    }

    override fun mouseMoved(e: MouseEvent) {
      showGrab()
    }

    private fun showGrab() {
      if (isPanning) {
        cursor = AdtUiCursors.GRAB
      }
      else {
        cursor = Cursor.getDefaultCursor()
      }
    }

    override fun mousePressed(e: MouseEvent) {
      contentPanel.requestFocus()
      if (currentlyPanning(e)) {
        cursor = AdtUiCursors.GRABBING
        lastPanMouseLocation = SwingUtilities.convertPoint(e.component, e.point, this@DeviceViewPanel)
        e.consume()
      }
    }

    override fun mouseDragged(e: MouseEvent) {
      val lastLocation = lastPanMouseLocation
      // convert to non-scrollable coordinates, otherwise as soon as the scroll is changed the mouse position also changes.
      val newLocation = SwingUtilities.convertPoint(e.component, e.point, this@DeviceViewPanel)
      lastPanMouseLocation = newLocation
      if (currentlyPanning(e) && lastLocation != null) {
        cursor = AdtUiCursors.GRABBING
        val extent = scrollPane.viewport.extentSize
        val view = scrollPane.viewport.viewSize
        val p = scrollPane.viewport.viewPosition
        p.translate(lastLocation.x - newLocation.x, lastLocation.y - newLocation.y)
        p.x = p.x.coerceIn(0, view.width - extent.width)
        p.y = p.y.coerceIn(0, view.height - extent.height)

        scrollPane.viewport.viewPosition = p
        e.consume()
      }
    }

    override fun mouseReleased(e: MouseEvent) {
      if (lastPanMouseLocation != null) {
        cursor = if (isPanning) AdtUiCursors.GRAB else Cursor.getDefaultCursor()
        lastPanMouseLocation = null
        e.consume()
      }
    }
  }

  private val scrollPane = JBScrollPane(contentPanel)
  private val layeredPane = JLayeredPane()
  private val deviceViewPanelActionsToolbar: DeviceViewPanelActionsToolbar

  init {
    contentPanel.isFocusable = true

    val mouseListeners = listOf(*contentPanel.mouseListeners)
    mouseListeners.forEach { contentPanel.removeMouseListener(it) }
    val mouseMotionListeners = listOf(*contentPanel.mouseMotionListeners)
    mouseMotionListeners.forEach { contentPanel.removeMouseMotionListener(it) }
    val keyboardListeners = listOf(*contentPanel.keyListeners)
    keyboardListeners.forEach { contentPanel.removeKeyListener(it) }
    contentPanel.addMouseListener(panMouseListener)
    contentPanel.addMouseMotionListener(panMouseListener)
    contentPanel.addKeyListener(object: KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == VK_SPACE) {
          isSpacePressed = true
        }
      }

      override fun keyReleased(e: KeyEvent) {
        if (e.keyCode == VK_SPACE) {
          isSpacePressed = false
        }
      }
    })
    mouseListeners.forEach { contentPanel.addMouseListener(it) }
    mouseMotionListeners.forEach { contentPanel.addMouseMotionListener(it) }
    keyboardListeners.forEach { contentPanel.addKeyListener(it) }

    scrollPane.border = JBUI.Borders.empty()
    val toolbarComponent = createToolbar()
    add(toolbarComponent, BorderLayout.NORTH)
    add(layeredPane, BorderLayout.CENTER)

    deviceViewPanelActionsToolbar = DeviceViewPanelActionsToolbar(this, disposableParent)

    val floatingToolbar = deviceViewPanelActionsToolbar.designSurfaceToolbar

    layeredPane.setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)
    layeredPane.setLayer(floatingToolbar, JLayeredPane.PALETTE_LAYER)

    layeredPane.layout = object: BorderLayout() {
      override fun layoutContainer(parent: Container?) {
        super.layoutContainer(parent)
        // Position the floating toolbar
        updateLayeredPaneSize()
      }
    }

    layeredPane.add(floatingToolbar)
    layeredPane.add(scrollPane, BorderLayout.CENTER)

    layoutInspector.layoutInspectorModel.modificationListeners.add { _, _, structural ->
      if (structural) {
        zoom(ZoomType.FIT)
      }
    }
    var prevZoom = viewSettings.scalePercent
    viewSettings.modificationListeners.add {
      if (prevZoom != viewSettings.scalePercent) {
        deviceViewPanelActionsToolbar.zoomChanged()
        prevZoom = viewSettings.scalePercent
      }
    }
  }

  private fun updateLayeredPaneSize() {
    scrollPane.size = layeredPane.size
    val floatingToolbar = deviceViewPanelActionsToolbar.designSurfaceToolbar
    floatingToolbar.size = floatingToolbar.preferredSize
    floatingToolbar.location = Point(layeredPane.width - floatingToolbar.width - TOOLBAR_INSET,
                                     layeredPane.height - floatingToolbar.height - TOOLBAR_INSET)
  }

  override fun zoom(type: ZoomType): Boolean {
    if (layoutInspector.layoutInspectorModel.isEmpty) {
      viewSettings.scalePercent = 100
      scrollPane.viewport.revalidate()
      return false
    }
    val root = layoutInspector.layoutInspectorModel.root
    val position = scrollPane.viewport.viewPosition.apply { translate(scrollPane.viewport.width / 2, scrollPane.viewport.height / 2) }
    position.x = (position.x / scale).toInt()
    position.y = (position.y / scale).toInt()
    when (type) {
      ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> {
        val availableWidth = scrollPane.width - scrollPane.verticalScrollBar.width
        val availableHeight = scrollPane.height - scrollPane.horizontalScrollBar.height
        val desiredWidth = (root.width).toDouble()
        val desiredHeight = (root.height).toDouble()
        viewSettings.scalePercent = if (desiredHeight == 0.0 || desiredWidth == 0.0) 100
        else (100 * min(availableHeight / desiredHeight, availableWidth / desiredWidth)).toInt()
      }
      ZoomType.ACTUAL -> viewSettings.scalePercent = 100
      ZoomType.IN -> viewSettings.scalePercent += 10
      ZoomType.OUT -> viewSettings.scalePercent -= 10
    }
    contentPanel.revalidate()

    ApplicationManager.getApplication().invokeLater {
      scrollPane.viewport.viewPosition = when (type) {
        ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> {
          val bounds = scrollPane.viewport.extentSize
          val size = scrollPane.viewport.view.preferredSize
          Point((size.width - bounds.width) / 2, (size.height - bounds.height) / 2)
        }
        else -> {
          position.x = (position.x * scale).toInt()
          position.y = (position.y * scale).toInt()
          position.translate(-scrollPane.viewport.width / 2, -scrollPane.viewport.height / 2)
          position
        }
      }
    }
    return true
  }

  override fun canZoomIn() = viewSettings.scalePercent < MAX_ZOOM && !layoutInspector.layoutInspectorModel.isEmpty

  override fun canZoomOut() = viewSettings.scalePercent > MIN_ZOOM && !layoutInspector.layoutInspectorModel.isEmpty

  override fun canZoomToFit() = !layoutInspector.layoutInspectorModel.isEmpty

  override fun canZoomToActual() = viewSettings.scalePercent < 100 && canZoomIn() || viewSettings.scalePercent > 100 && canZoomOut()

  override fun getData(dataId: String): Any? {
    if (ZOOMABLE_KEY.`is`(dataId) || PANNABLE_KEY.`is`(dataId)) {
      return this
    }
    if (DEVICE_VIEW_MODEL_KEY.`is`(dataId)) {
      return contentPanel.model
    }
    if (DEVICE_VIEW_SETTINGS_KEY.`is`(dataId)) {
      return viewSettings
    }
    return null
  }

  override val isPannable: Boolean
    get() = contentPanel.width > scrollPane.viewport.width || contentPanel.height > scrollPane.viewport.height

  private fun createToolbar(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, com.android.tools.adtui.common.border)!!

    val leftPanel = AdtPrimaryPanel(BorderLayout())
    val leftGroup = DefaultActionGroup()
    leftGroup.add(SelectProcessAction(layoutInspector))
    leftGroup.add(Separator.getInstance())
    leftGroup.add(ViewMenuAction)
    leftGroup.add(ToggleOverlayAction)
    leftGroup.add(AlphaSliderAction)
    leftGroup.add(Separator.getInstance())
    leftGroup.add(PauseLayoutInspectorAction(layoutInspector::currentClient))
    leftGroup.add(CaptureAction(layoutInspector::currentClient, layoutInspector.layoutInspectorModel))
    val actionToolbar = ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorLeft", leftGroup, true)
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
    actionToolbar.component.name = DEVICE_VIEW_ACTION_TOOLBAR_NAME
    actionToolbar.setTargetComponent(this)
    leftPanel.add(actionToolbar.component, BorderLayout.CENTER)
    panel.add(leftPanel, BorderLayout.CENTER)
    return panel
  }

  private class PauseLayoutInspectorAction(val client: () -> InspectorClient) : CheckboxAction("Live updates") {

    override fun update(event: AnActionEvent) {
      event.presentation.isEnabled = client().isConnected && client().selectedStream.device.featureLevel >= 29
      super.update(event)
      event.presentation.description = if (event.presentation.isEnabled) null else "Live updates not available for devices below API 29"
    }

    // Display as "Live updates ON" when disconnected to indicate the default value after the inspector is connected to the device.
    override fun isSelected(event: AnActionEvent): Boolean {
      return !client().isConnected || client().isCapturing
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      if (!client().isConnected) {
        return
      }
      val command = if (client().isCapturing) LayoutInspectorCommand.Type.STOP else LayoutInspectorCommand.Type.START
      client().execute(command)
    }
  }
}

