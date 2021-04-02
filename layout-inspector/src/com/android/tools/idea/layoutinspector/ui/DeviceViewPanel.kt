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
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.adtui.common.helpText
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.ex.TooltipLinkProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBLoadingPanelListener
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import icons.StudioIcons.LayoutInspector.LIVE_UPDATES
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.LayoutManager
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_SPACE
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.util.concurrent.Executors.newSingleThreadExecutor
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
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
  private val processes: ProcessesModel,
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

  private val contentPanel = DeviceViewContentPanel(layoutInspector.layoutInspectorModel, viewSettings, disposableParent)

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
      cursor = if (isPanning) {
        AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRAB)
      }
      else {
        Cursor.getDefaultCursor()
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
  private val loadingPane: JBLoadingPanel
  private val deviceViewPanelActionsToolbar: DeviceViewPanelActionsToolbarProvider
  private val viewportLayoutManager = MyViewportLayoutManager(scrollPane.viewport, { contentPanel.model.layerSpacing },
                                                              { contentPanel.rootLocation })

  private val actionToolbar: ActionToolbar

  private val bubbleLabel = JLabel()

  private val bubble = object: JPanel(FlowLayout()) {
    init {
      add(JLabel(StudioIcons.Common.INFO_INLINE))
      add(bubbleLabel)
      isOpaque = false
      border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
      isVisible = false
    }

    override fun paint(g: Graphics) {
      g.color = JBColor.WHITE
      g.fillRoundRect(0, 1, width, height - 2, height - 2, height - 2)
      g.color = helpText
      super.paint(g)
    }
  }

  private var infoText: String?
    get() = bubbleLabel.text
    set(text) {
      bubbleLabel.text = text
      bubble.isVisible = !text.isNullOrBlank()
    }

  init {
    loadingPane = JBLoadingPanel(BorderLayout(), disposableParent)
    loadingPane.addListener(object: JBLoadingPanelListener {
      override fun onLoadingStart() {
        contentPanel.showEmptyText = false
      }

      override fun onLoadingFinish() {
        contentPanel.showEmptyText = true
      }
    })
    val selectProcessAction = SelectProcessAction(processes,
                                                  supportsOffline = false,
                                                  createProcessLabel = (SelectProcessAction)::createCompactProcessLabel,
                                                  stopPresentation = SelectProcessAction.StopPresentation(
                                                    "Stop inspector",
                                                    "Stop running the layout inspector against the current process"),
                                                  onStopAction = { stopInspectors() })
    contentPanel.selectProcessAction = selectProcessAction
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

    actionToolbar = createToolbar(selectProcessAction)
    val toolbarComponent = createToolbarPanel(actionToolbar)
    add(toolbarComponent, BorderLayout.NORTH)
    loadingPane.add(layeredPane, BorderLayout.CENTER)
    add(loadingPane, BorderLayout.CENTER)
    val model = layoutInspector.layoutInspectorModel
    processes.addSelectedProcessListeners(newSingleThreadExecutor()) {
      if (processes.selectedProcess?.isRunning == true) {
        if (model.isEmpty) {
          loadingPane.startLoading()
        }
      }
    }
    model.modificationListeners.add { old, new, _ ->
      if (old == null && new != null) {
        loadingPane.stopLoading()
      }
    }
    contentPanel.model.modificationListeners.add {
      ApplicationManager.getApplication().invokeLater {
        actionToolbar.updateActionsImmediately()
        infoText = when {
          layoutInspector.currentClient.isCapturing && contentPanel.model.isRotated ->
            "Device performance reduced when inspecting in 3D mode with Live Updates enabled"
          layoutInspector.currentClient.isCapturing && model.hasHiddenNodes() ->
            "Device performance reduced when views are hidden and Live Updates are enabled"
          else -> null
        }
      }
    }

    deviceViewPanelActionsToolbar = DeviceViewPanelActionsToolbarProvider(this, disposableParent)

    val floatingToolbar = deviceViewPanelActionsToolbar.floatingToolbar

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

    layeredPane.setLayer(bubble, JLayeredPane.PALETTE_LAYER)

    layeredPane.add(bubble)
    layeredPane.add(scrollPane, BorderLayout.CENTER)

    // Zoom to fit on initial connect
    model.modificationListeners.add { _, new, _ ->
      if (contentPanel.model.maxWidth == 0) {
        contentPanel.model.refresh()
        if (!zoom(ZoomType.FIT)) {
          // If we didn't change the zoom, we need to refresh explicitly. Otherwise the zoom listener will do it.
          new?.refreshImages(viewSettings.scaleFraction)
          contentPanel.model.refresh()
        }
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
      val client = LayoutInspector.get(this@DeviceViewPanel)?.currentClient
      if (client?.isCapturing == true) {
        client.updateScreenshotType(null, viewSettings.scaleFraction.toFloat())
      }
      if (prevZoom != viewSettings.scalePercent) {
        ApplicationManager.getApplication().executeOnPooledThread {
          deviceViewPanelActionsToolbar.zoomChanged(prevZoom / 100.0, viewSettings.scalePercent / 100.0)
          prevZoom = viewSettings.scalePercent
          model.windows.values.forEach {
            it.refreshImages(viewSettings.scaleFraction)
          }
          contentPanel.model.refresh()
        }
      }
    }
  }

  fun stopInspectors() {
    loadingPane.stopLoading()
    processes.stop()
  }

  private fun updateLayeredPaneSize() {
    scrollPane.size = layeredPane.size
    val floatingToolbar = deviceViewPanelActionsToolbar.floatingToolbar
    floatingToolbar.size = floatingToolbar.preferredSize
    floatingToolbar.location = Point(layeredPane.width - floatingToolbar.width - TOOLBAR_INSET,
                                     layeredPane.height - floatingToolbar.height - TOOLBAR_INSET)
    bubble.size = bubble.preferredSize
    bubble.location = Point(TOOLBAR_INSET, layeredPane.height - bubble.height - TOOLBAR_INSET)
  }

  override fun zoom(type: ZoomType): Boolean {
    var newZoom = viewSettings.scalePercent
    if (layoutInspector.layoutInspectorModel.isEmpty) {
      newZoom = 100
      scrollPane.viewport.revalidate()
    }
    else {
      val root = layoutInspector.layoutInspectorModel.root
      viewportLayoutManager.currentZoomOperation = type
      when (type) {
        ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> {
          newZoom = getFitZoom(root)
        }
        ZoomType.ACTUAL -> newZoom = 100
        ZoomType.IN -> newZoom += 10
        ZoomType.OUT -> newZoom -= 10
      }
      newZoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }
    if (newZoom != viewSettings.scalePercent) {
      viewSettings.scalePercent = newZoom
      contentPanel.revalidate()
      return true
    }

    return false
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
    if (TOGGLE_3D_ACTION_BUTTON_KEY.`is`(dataId)) {
      return deviceViewPanelActionsToolbar.toggle3dActionButton
    }
    return null
  }

  override val isPannable: Boolean
    get() = contentPanel.width > scrollPane.viewport.width || contentPanel.height > scrollPane.viewport.height

  private fun createToolbar(selectProcessAction: AnAction): ActionToolbar {
    val leftGroup = DefaultActionGroup()
    leftGroup.add(selectProcessAction)
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

  object PauseLayoutInspectorAction : ToggleAction({ "Live Updates" }, LIVE_UPDATES), TooltipDescriptionProvider, TooltipLinkProvider {

    override fun update(event: AnActionEvent) {
      val currentClient = client(event)
      val isLiveInspector = !currentClient.isConnected || currentClient.capabilities.contains(Capability.SUPPORTS_CONTINUOUS_MODE)
      val isLowerThenApi29 = currentClient.isConnected && currentClient.process.device.apiLevel < 29
      event.presentation.isEnabled = isLiveInspector || !currentClient.isConnected
      super.update(event)
      event.presentation.description = when {
        isLowerThenApi29 -> "Live updates not available for devices below API 29"
        !isLiveInspector -> AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY)
        else -> "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device " +
                "resources and might impact runtime performance."
      }
    }

    override fun getTooltipLink(owner: JComponent?) = TooltipLinkProvider.TooltipLink("Learn More") {
      Desktop.getDesktop().browse(URI("https://d.android.com/r/studio-ui/layout-inspector-live-updates"))
    }

    // When disconnected: display the default value after the inspector is connected to the device.
    override fun isSelected(event: AnActionEvent): Boolean {
      return InspectorClientSettings.isCapturingModeOn
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      event.getData(DEVICE_VIEW_MODEL_KEY)?.fireModified()
      val currentClient = client(event)
      if (currentClient.capabilities.contains(Capability.SUPPORTS_CONTINUOUS_MODE)) {
        when (state) {
          true -> currentClient.startFetching()
          false -> currentClient.stopFetching()
        }
      }
      InspectorClientSettings.isCapturingModeOn = state
    }

    private fun client(event: AnActionEvent): InspectorClient =
      LayoutInspector.get(event)?.currentClient ?: DisconnectedClient
  }
}

@VisibleForTesting
class MyViewportLayoutManager(
  private val viewport: JViewport,
  private val layerSpacing: () -> Int,
  private val rootLocation: () -> Point?
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
        val currentRootLocation = rootLocation()
        if (viewport.view.size != lastViewSize && lastRoot != null && currentRootLocation != null) {
          val newRootLocation = SwingUtilities.convertPoint(viewport.view, currentRootLocation, viewport)
          viewport.viewPosition = Point(viewport.viewPosition).apply {
            translate(newRootLocation.x - lastRoot.x, newRootLocation.y - lastRoot.y)
          }
        }
      }
    }
    lastRootLocation = rootLocation()?.let { SwingUtilities.convertPoint(viewport.view, it, viewport) }
    lastViewSize = viewport.view.size
  }
}

