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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.legacydevice.CaptureAction
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.min

private const val MAX_ZOOM = 300
private const val MIN_ZOOM = 10

private const val TOOLBAR_INSET = 14

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel(
  val layoutInspector: LayoutInspector,
  private val viewSettings: DeviceViewSettings,
  disposableParent: Disposable
) : JPanel(BorderLayout()), Zoomable, DataProvider, Pannable {

  override val scale
    get() = viewSettings.scaleFraction

  override val screenScalingFactor = 1f

  private val contentPanel = DeviceViewContentPanel(layoutInspector.layoutInspectorModel, viewSettings)
  private val panInterceptorPanel = JPanel()

  private val scrollPane = JBScrollPane(contentPanel)
  private val layeredPane = JLayeredPane()
  private val deviceViewPanelActionsToolbar: DeviceViewPanelActionsToolbar

  override var isPanning = false
  private var lastPanMouseLocation: Point? = null

  private val panMouseListener = object : MouseAdapter() {
    private fun currentlyPanning(e: MouseEvent) = isPanning || SwingUtilities.isMiddleMouseButton(e)

    private fun redispatch(e: MouseEvent?) {
      val retargetedEvent = SwingUtilities.convertMouseEvent(panInterceptorPanel, e, contentPanel)
      contentPanel.dispatchEvent(retargetedEvent)
    }

    override fun mouseClicked(e: MouseEvent?) {
      redispatch(e)
    }

    override fun mouseExited(e: MouseEvent?) {
      redispatch(e)
    }

    override fun mouseWheelMoved(e: MouseWheelEvent?) {
      redispatch(e)
    }

    override fun mouseEntered(e: MouseEvent) {
      showGrab(e)
    }

    override fun mouseMoved(e: MouseEvent) {
      showGrab(e)
    }

    private fun showGrab(e: MouseEvent) {
      if (isPanning) {
        cursor = AdtUiCursors.GRAB
      }
      else {
        cursor = Cursor.getDefaultCursor()
      }
      redispatch(e)
    }

    override fun mousePressed(e: MouseEvent) {
      if (currentlyPanning(e)) {
        cursor = AdtUiCursors.GRABBING
        lastPanMouseLocation = e.point
      }
      else {
        redispatch(e)
      }
    }

    override fun mouseDragged(e: MouseEvent) {
      val lastLocation = lastPanMouseLocation
      lastPanMouseLocation = e.point
      if (currentlyPanning(e) && lastLocation != null) {
        cursor = AdtUiCursors.GRABBING
        val extent = scrollPane.viewport.extentSize
        val view = scrollPane.viewport.viewSize
        val p = scrollPane.viewport.viewPosition
        p.translate(lastLocation.x - e.x, lastLocation.y - e.y)
        p.x = p.x.coerceIn(0, view.width - extent.width)
        p.y = p.y.coerceIn(0, view.height - extent.height)

        scrollPane.viewport.viewPosition = p
      }
      else {
        redispatch(e)
      }
    }

    override fun mouseReleased(e: MouseEvent) {
      if (lastPanMouseLocation != null) {
        cursor = if (isPanning) AdtUiCursors.GRAB else Cursor.getDefaultCursor()
        lastPanMouseLocation = null
      }
      else {
        redispatch(e)
      }
    }
  }


  init {
    scrollPane.border = JBUI.Borders.empty()
    val toolbarComponent = createToolbar()
    add(toolbarComponent, BorderLayout.NORTH)
    add(layeredPane, BorderLayout.CENTER)

    deviceViewPanelActionsToolbar = DeviceViewPanelActionsToolbar(this, disposableParent)

    val floatingToolbar = deviceViewPanelActionsToolbar.designSurfaceToolbar

    layeredPane.setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)
    layeredPane.setLayer(panInterceptorPanel, 50)
    layeredPane.setLayer(floatingToolbar, JLayeredPane.PALETTE_LAYER)
    layeredPane.add(scrollPane)
    layeredPane.add(panInterceptorPanel)
    layeredPane.add(floatingToolbar)

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
    layeredPane.addComponentListener(object: ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateLayeredPaneSize()
      }
    })

    panInterceptorPanel.isOpaque = false
    panInterceptorPanel.addMouseListener(panMouseListener)
    panInterceptorPanel.addMouseMotionListener(panMouseListener)
  }

  private fun updateLayeredPaneSize() {
    scrollPane.size = layeredPane.size
    panInterceptorPanel.size = layeredPane.size
    val floatingToolbar = deviceViewPanelActionsToolbar.designSurfaceToolbar
    floatingToolbar.size = floatingToolbar.preferredSize
    floatingToolbar.location = Point(layeredPane.width - floatingToolbar.width - TOOLBAR_INSET,
                                     layeredPane.height - floatingToolbar.height - TOOLBAR_INSET)
  }

  override fun zoom(type: ZoomType): Boolean {
    val root = layoutInspector.layoutInspectorModel.root
    if (root == null) {
      viewSettings.scalePercent = 100
      scrollPane.viewport.revalidate()
      return false
    }
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
    updateLayeredPaneSize()

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

  override fun canZoomIn() = viewSettings.scalePercent < MAX_ZOOM && layoutInspector.layoutInspectorModel.root != null

  override fun canZoomOut() = viewSettings.scalePercent > MIN_ZOOM && layoutInspector.layoutInspectorModel.root != null

  override fun canZoomToFit() = layoutInspector.layoutInspectorModel.root != null

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
    leftGroup.add(ViewMenuAction)
    leftGroup.add(PauseLayoutInspectorAction(layoutInspector::currentClient))
    leftGroup.add(ToggleOverlayAction)
    leftGroup.add(AlphaSliderAction)
    if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_LEGACY_DEVICE_SUPPORT.get()) {
      leftGroup.add(CaptureAction(layoutInspector::currentClient, layoutInspector.layoutInspectorModel))
    }
    val actionToolbar = ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorLeft", leftGroup, true)
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
    actionToolbar.setTargetComponent(this)
    leftPanel.add(actionToolbar.component, BorderLayout.CENTER)
    panel.add(leftPanel, BorderLayout.CENTER)
    return panel
  }

  private class PauseLayoutInspectorAction(val client: () -> InspectorClient) : CheckboxAction("Live updates") {

    override fun update(event: AnActionEvent) {
      super.update(event)
      event.presentation.isVisible = client().isConnected && client().selectedStream.device.apiLevel >= 29
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

