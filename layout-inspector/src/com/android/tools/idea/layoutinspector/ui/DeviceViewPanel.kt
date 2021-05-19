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
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.editor.ActionToolbarUtil
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.transport.DisconnectedClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.layoutinspector.transport.isCapturingModeOn
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.LayoutManager
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
import javax.swing.JViewport
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

  override val screenScalingFactor = 1.0

  override var isPanning = false
  private var isSpacePressed = false
  private var lastPanMouseLocation: Point? = null

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
        cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRAB)
      }
      else {
        cursor = Cursor.getDefaultCursor()
      }
    }

    override fun mousePressed(e: MouseEvent) {
      contentPanel.requestFocus()
      if (currentlyPanning(e)) {
        cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRABBING)
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
        cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRABBING)
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
        cursor = if (isPanning) AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRAB) else Cursor.getDefaultCursor()
        lastPanMouseLocation = null
        e.consume()
      }
    }
  }

  private val scrollPane = JBScrollPane(contentPanel)
  private val layeredPane = JLayeredPane()
  private val deviceViewPanelActionsToolbar: DeviceViewPanelActionsToolbar
  private val viewportLayoutManager = MyViewportLayoutManager(scrollPane.viewport, { contentPanel.model.layerSpacing },
                                                              { contentPanel.rootLocation })

  private val actionToolbar: ActionToolbar

  init {
    scrollPane.viewport.layout = viewportLayoutManager
    contentPanel.isFocusable = true

    val mouseListeners = listOf(*contentPanel.mouseListeners)
    mouseListeners.forEach { contentPanel.removeMouseListener(it) }
    val mouseMotionListeners = listOf(*contentPanel.mouseMotionListeners)
    mouseMotionListeners.forEach { contentPanel.removeMouseMotionListener(it) }
    val keyboardListeners = listOf(*contentPanel.keyListeners)
    keyboardListeners.forEach { contentPanel.removeKeyListener(it) }
    contentPanel.addMouseListener(panMouseListener)
    contentPanel.addMouseMotionListener(panMouseListener)
    contentPanel.addKeyListener(object : KeyAdapter() {
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
    actionToolbar = createToolbar()
    val toolbarComponent = createToolbarPanel(actionToolbar)
    add(toolbarComponent, BorderLayout.NORTH)
    add(layeredPane, BorderLayout.CENTER)
    contentPanel.model.modificationListeners.add {
      ApplicationManager.getApplication().invokeLater {
        actionToolbar.updateActionsImmediately()
      }
    }

    deviceViewPanelActionsToolbar = DeviceViewPanelActionsToolbar(this, disposableParent)

    val floatingToolbar = deviceViewPanelActionsToolbar.designSurfaceToolbar

    layeredPane.setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)
    layeredPane.setLayer(floatingToolbar, JLayeredPane.PALETTE_LAYER)

    layeredPane.layout = object : BorderLayout() {
      override fun layoutContainer(parent: Container?) {
        super.layoutContainer(parent)
        // Position the floating toolbar
        updateLayeredPaneSize()
      }
    }

    layeredPane.add(floatingToolbar)
    layeredPane.add(scrollPane, BorderLayout.CENTER)

    // Zoom to fit on initial connect
    layoutInspector.layoutInspectorModel.modificationListeners.add { old, new, _ ->
      if (old == null) {
        contentPanel.model.refresh()
        zoom(ZoomType.FIT)
      }
      else {
        // refreshImages is done here instead of by the model itself so that we can be sure to zoom to fit first before trying to render
        // images upon first connecting.
        new?.refreshImages(viewSettings.scaleFraction)
        contentPanel.model.refresh()
      }
    }
    var prevZoom = viewSettings.scalePercent
    viewSettings.modificationListeners.add {
      if (prevZoom != viewSettings.scalePercent) {
        ApplicationManager.getApplication().executeOnPooledThread {
          deviceViewPanelActionsToolbar.zoomChanged()
          prevZoom = viewSettings.scalePercent
          layoutInspector.layoutInspectorModel.windows.values.forEach {
            it.refreshImages(viewSettings.scaleFraction)
          }
          contentPanel.model.refresh()
        }
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
    viewportLayoutManager.currentZoomOperation = type
    when (type) {
      ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> {
        viewSettings.scalePercent = getFitZoom(root)
      }
      ZoomType.ACTUAL -> viewSettings.scalePercent = 100
      ZoomType.IN -> viewSettings.scalePercent += 10
      ZoomType.OUT -> viewSettings.scalePercent -= 10
    }
    viewSettings.scalePercent = viewSettings.scalePercent.coerceIn(MIN_ZOOM, MAX_ZOOM)
    contentPanel.revalidate()

    return true
  }

  private fun getFitZoom(root: ViewNode): Int {
    val availableWidth = scrollPane.width - scrollPane.verticalScrollBar.width
    val availableHeight = scrollPane.height - scrollPane.horizontalScrollBar.height
    val desiredWidth = (root.width).toDouble()
    val desiredHeight = (root.height).toDouble()
    return if (desiredHeight == 0.0 || desiredWidth == 0.0) 100
    else (90 * min(availableHeight / desiredHeight, availableWidth / desiredWidth)).toInt()
  }

  override fun canZoomIn() = viewSettings.scalePercent < MAX_ZOOM && !layoutInspector.layoutInspectorModel.isEmpty

  override fun canZoomOut() = viewSettings.scalePercent > MIN_ZOOM && !layoutInspector.layoutInspectorModel.isEmpty

  override fun canZoomToFit() = !layoutInspector.layoutInspectorModel.isEmpty &&
                                getFitZoom(layoutInspector.layoutInspectorModel.root) != viewSettings.scalePercent

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

  private fun createToolbar(): ActionToolbar {
    val leftGroup = DefaultActionGroup()
    leftGroup.add(SelectProcessAction(layoutInspector))
    leftGroup.add(Separator.getInstance())
    leftGroup.add(ViewMenuAction)
    leftGroup.add(ToggleOverlayAction)
    leftGroup.add(AlphaSliderAction)
    leftGroup.add(Separator.getInstance())
    leftGroup.add(PauseLayoutInspectorAction)
    leftGroup.add(RefreshAction)
    leftGroup.add(Separator.getInstance())
    leftGroup.add(LayerSpacingSliderAction)
    val actionToolbar = ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorLeft", leftGroup, true)
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
    actionToolbar.component.name = DEVICE_VIEW_ACTION_TOOLBAR_NAME
    actionToolbar.setTargetComponent(this)
    return actionToolbar
  }

  private fun createToolbarPanel(actionToolbar: ActionToolbar): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, com.android.tools.adtui.common.border)!!

    val leftPanel = AdtPrimaryPanel(BorderLayout())
    leftPanel.add(actionToolbar.component, BorderLayout.CENTER)
    panel.add(leftPanel, BorderLayout.CENTER)
    return panel
  }

  object PauseLayoutInspectorAction : CheckboxAction("Live updates") {

    override fun update(event: AnActionEvent) {
      val currentClient = client(event)
      val isLiveInspector = currentClient.isConnected && currentClient is DefaultInspectorClient
      val isLowerThenApi29 = currentClient.isConnected && currentClient.selectedStream.device.featureLevel < 29
      event.presentation.isEnabled = isLiveInspector || !currentClient.isConnected
      super.update(event)
      event.presentation.description = when {
        !currentClient.isConnected -> null
        isLowerThenApi29 -> "Live updates not available for devices below API 29"
        !isLiveInspector -> AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY)
        else -> null
      }
    }

    // When disconnected: display the default value after the inspector is connected to the device.
    override fun isSelected(event: AnActionEvent): Boolean {
      return isCapturingModeOn
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      val currentClient = client(event)
      if (!currentClient.isConnected) {
        isCapturingModeOn = state
      }
      else {
        (currentClient as? DefaultInspectorClient)?.isCapturing = state
      }
    }

    private fun client(event: AnActionEvent): InspectorClient =
      event.getData(LAYOUT_INSPECTOR_DATA_KEY)?.currentClient ?: DisconnectedClient
  }
}

@VisibleForTesting
class MyViewportLayoutManager(
  private val viewport: JViewport,
  private val layerSpacing: () -> Int,
  private val rootLocation: () -> Point
) : LayoutManager by viewport.layout {
  private var lastLayerSpacing = INITIAL_LAYER_SPACING
  private var lastRootLocation: Point? = null
  private val origLayout = viewport.layout
  private var lastViewSize: Dimension? = null

  var currentZoomOperation: ZoomType? = null

  override fun layoutContainer(parent: Container?) {
    when {
      layerSpacing() != lastLayerSpacing -> {
        lastLayerSpacing = layerSpacing()
        val position = viewport.viewPosition.apply { translate(-viewport.view.width / 2, -viewport.view.height / 2) }
        origLayout.layoutContainer(parent)
        viewport.viewPosition = position.apply { translate(viewport.view.width / 2, viewport.view.height / 2) }
      }
      currentZoomOperation != null -> {
        viewport.viewPosition = when (currentZoomOperation) {
          ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> {
            origLayout.layoutContainer(parent)
            val bounds = viewport.extentSize
            val size = viewport.view.preferredSize
            Point((size.width - bounds.width).coerceAtLeast(0) / 2, (size.height - bounds.height).coerceAtLeast(0) / 2)
          }
          else -> {
            val position = SwingUtilities.convertPoint(viewport, Point(viewport.width/2, viewport.height/2), viewport.view)
            val xPercent = position.x.toDouble() / viewport.view.width.toDouble()
            val yPercent = position.y.toDouble() / viewport.view.height.toDouble()

            origLayout.layoutContainer(parent)

            val newPosition = Point((viewport.view.width * xPercent).toInt(), (viewport.view.height * yPercent).toInt())
            newPosition.translate(-viewport.extentSize.width/2, -viewport.extentSize.height/2)
            newPosition
          }
        }
        currentZoomOperation = null
      }
      else -> {
        origLayout.layoutContainer(parent)
        val lastRoot = lastRootLocation
        if (viewport.view.size != lastViewSize && lastRoot != null) {
          val newRootLocation = SwingUtilities.convertPoint(viewport.view, rootLocation(), viewport)
          viewport.viewPosition = Point(viewport.viewPosition).apply {
            translate(newRootLocation.x - lastRoot.x, newRootLocation.y - lastRoot.y)
          }
        }
      }
    }
    lastRootLocation = SwingUtilities.convertPoint(viewport.view, rootLocation(), viewport)
    lastViewSize = viewport.view.size
  }
}

