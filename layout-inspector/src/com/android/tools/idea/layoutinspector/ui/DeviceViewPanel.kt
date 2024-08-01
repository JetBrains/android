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
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcess
import com.android.tools.idea.layoutinspector.ui.toolbar.FloatingToolbarProvider
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.INITIAL_LAYER_SPACING
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.TargetSelectionActionFactory
import com.android.tools.idea.layoutinspector.ui.toolbar.createStandaloneLayoutInspectorToolbar
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.EditorNotificationPanel.Status
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.launch
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

const val PERFORMANCE_WARNING_3D = "performance.warning.3d"
const val PERFORMANCE_WARNING_HIDDEN = "performance.warning.hidden"

val TOGGLE_3D_ACTION_BUTTON_KEY = DataKey.create<ActionButton>("Toggle3DActionButtonKey")

/** Panel that shows the device screen in the layout inspector. */
class DeviceViewPanel(val layoutInspector: LayoutInspector, disposableParent: Disposable) :
  JPanel(BorderLayout()), Zoomable, UiDataProvider, Pannable {

  private val renderSettings = layoutInspector.renderLogic.renderSettings

  override val scale
    get() = renderSettings.scaleFraction

  override val screenScalingFactor = 1.0

  override var isPanning = false
    get() =
      (field || isMiddleMousePressed || isSpacePressed) &&
        (layoutInspector.isSnapshot || layoutInspector.processModel?.selectedProcess != null)

  private var isSpacePressed = false
  private var isMiddleMousePressed = false
  private var lastPanMouseLocation: Point? = null
  private var performanceWarningGiven = false

  private val targetSelectedAction = TargetSelectionActionFactory.getAction(layoutInspector)

  private val layoutInspectorLoadingObserver =
    LayoutInspectorLoadingObserver(disposableParent, layoutInspector)

  private val contentPanel =
    DeviceViewContentPanel(
      inspectorModel = layoutInspector.inspectorModel,
      deviceModel = layoutInspector.deviceModel,
      treeSettings = layoutInspector.treeSettings,
      currentClient = { layoutInspector.currentClient },
      pannable = this,
      selectTargetAction = targetSelectedAction,
      disposableParent = disposableParent,
      isLoading = { layoutInspectorLoadingObserver.isLoading },
      isCurrentForegroundProcessDebuggable = { isCurrentForegroundProcessDebuggable },
      hasForegroundProcess = { hasForegroundProcess },
      renderLogic = layoutInspector.renderLogic,
      renderModel = layoutInspector.renderModel,
      coroutineScope = layoutInspector.coroutineScope,
    )

  private fun showGrab() {
    cursor =
      if (isPanning) {
        AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRAB)
      } else {
        Cursor.getDefaultCursor()
      }
  }

  private val panMouseListener: MouseAdapter =
    object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        showGrab()
      }

      override fun mouseMoved(e: MouseEvent) {
        showGrab()
      }

      override fun mousePressed(e: MouseEvent) {
        contentPanel.requestFocus()
        isMiddleMousePressed = SwingUtilities.isMiddleMouseButton(e)
        if (isPanning) {
          cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRABBING)
          lastPanMouseLocation =
            SwingUtilities.convertPoint(e.component, e.point, this@DeviceViewPanel)
          e.consume()
        }
      }

      override fun mouseDragged(e: MouseEvent) {
        val lastLocation = lastPanMouseLocation
        // convert to non-scrollable coordinates, otherwise as soon as the scroll is changed the
        // mouse position also changes.
        val newLocation = SwingUtilities.convertPoint(e.component, e.point, this@DeviceViewPanel)
        lastPanMouseLocation = newLocation
        if (isPanning && lastLocation != null) {
          cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRABBING)
          val extent = scrollPane.viewport.extentSize
          val view = scrollPane.viewport.viewSize
          val p = scrollPane.viewport.viewPosition
          p.translate(lastLocation.x - newLocation.x, lastLocation.y - newLocation.y)
          val availableWidth = (view.width - extent.width).coerceAtLeast(0)
          val availableHeight = (view.height - extent.height).coerceAtLeast(0)
          p.x = p.x.coerceIn(0, availableWidth)
          p.y = p.y.coerceIn(0, availableHeight)

          scrollPane.viewport.viewPosition = p
          e.consume()
        }
      }

      override fun mouseReleased(e: MouseEvent) {
        isMiddleMousePressed = false
        if (lastPanMouseLocation != null) {
          cursor =
            if (isPanning) AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRAB)
            else Cursor.getDefaultCursor()
          lastPanMouseLocation = null
          e.consume()
        }
      }
    }

  private val scrollPane = JBScrollPane(contentPanel)
  private val layeredPane = JLayeredPane()
  private val loadingPane: JBLoadingPanel = JBLoadingPanel(BorderLayout(), disposableParent)
  private val floatingToolbarProvider = FloatingToolbarProvider(this, disposableParent)
  private val viewportLayoutManager =
    MyViewportLayoutManager(
      scrollPane.viewport,
      { contentPanel.renderModel.layerSpacing },
      { contentPanel.rootLocation },
    )

  private val actionToolbar =
    createStandaloneLayoutInspectorToolbar(
      disposableParent,
      this,
      layoutInspector,
      targetSelectedAction?.dropDownAction,
    )

  private var isCurrentForegroundProcessDebuggable = false
  private var hasForegroundProcess = false

  /**
   * If the new [ForegroundProcess] is not debuggable (it's not present in [ProcessesModel]),
   * [DeviceViewContentPanel] will show an error message.
   */
  fun onNewForegroundProcess(isDebuggable: Boolean) {
    hasForegroundProcess = true
    isCurrentForegroundProcessDebuggable = isDebuggable
  }

  init {
    layoutInspectorLoadingObserver.listeners.add(
      object : LayoutInspectorLoadingObserver.Listener {
        override fun onStartLoading() {
          loadingPane.startLoading()
        }

        override fun onStopLoading() {
          loadingPane.stopLoading()
        }
      }
    )

    layoutInspector.deviceModel?.newSelectedDeviceListeners?.add { _ ->
      // as soon as a new device is connected default to the process not being debuggable.
      // this will change as soon as an actual process shows up
      // and protects us against cases when the device has no foreground process (eg. is locked)
      hasForegroundProcess = false
    }

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
    contentPanel.addKeyListener(
      object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == VK_SPACE) {
            isSpacePressed = true
            showGrab()
          }
        }

        override fun keyReleased(e: KeyEvent) {
          if (e.keyCode == VK_SPACE) {
            isSpacePressed = false
            showGrab()
          }
        }
      }
    )
    mouseListeners.forEach { contentPanel.addMouseListener(it) }
    mouseMotionListeners.forEach { contentPanel.addMouseMotionListener(it) }
    keyboardListeners.forEach { contentPanel.addKeyListener(it) }

    scrollPane.border = JBUI.Borders.empty()

    val toolbarComponent = createToolbarPanel(actionToolbar)
    add(toolbarComponent, BorderLayout.NORTH)
    loadingPane.add(layeredPane, BorderLayout.CENTER)
    add(loadingPane, BorderLayout.CENTER)
    val model = layoutInspector.inspectorModel
    val notificationModel = layoutInspector.notificationModel

    model.addAttachStageListener(AttachProgressProvider { loadingPane.setLoadingText(it) })

    contentPanel.renderModel.modificationListeners.add {
      ApplicationManager.getApplication().invokeLater {
        val performanceWarningNeeded =
          layoutInspector.currentClient.inLiveMode &&
            (contentPanel.renderModel.isRotated || model.hasHiddenNodes())
        if (performanceWarningNeeded != performanceWarningGiven) {
          if (performanceWarningNeeded) {
            when {
              contentPanel.renderModel.isRotated -> PERFORMANCE_WARNING_3D
              model.hasHiddenNodes() -> PERFORMANCE_WARNING_HIDDEN
              else -> null
            }?.let {
              notificationModel.addNotification(
                it,
                LayoutInspectorBundle.message(it),
                Status.Warning,
              )
            }
          } else {
            notificationModel.removeNotification(PERFORMANCE_WARNING_3D)
            notificationModel.removeNotification(PERFORMANCE_WARNING_HIDDEN)
          }
        }
        performanceWarningGiven = performanceWarningNeeded
      }
    }

    layeredPane.setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)
    layeredPane.setLayer(floatingToolbarProvider.floatingToolbar, JLayeredPane.PALETTE_LAYER)

    layeredPane.layout =
      object : BorderLayout() {
        override fun layoutContainer(parent: Container?) {
          super.layoutContainer(parent)
          // Position the floating toolbar
          updateLayeredPaneSize()
        }
      }

    layeredPane.add(floatingToolbarProvider.floatingToolbar)
    layeredPane.add(scrollPane, BorderLayout.CENTER)

    var shouldZoomToFit = true
    layoutInspector.processModel?.addSelectedProcessListeners { shouldZoomToFit = true }

    model.addModificationListener { oldWindow, newWindow, _ ->
      if (oldWindow == null && newWindow != null) {
        // TODO(b/265150325) move to a more generic place
        layoutInspector.currentClient.stats.recompositionHighlightColor =
          renderSettings.highlightColor

        if (shouldZoomToFit) {
          // Zoom to fit each time a new window shows up immediately after a process change
          // we should do this only after a process change, because the new window showing up could
          // be a dialog being open, in which case
          // we don't want to change the zoom.
          // And we should do it only if the new window is different from null, so we know the view
          // is available and we can scroll to
          // center it.
          zoom(ZoomType.FIT)
          shouldZoomToFit = false
        }
      }
    }
    var prevZoom = renderSettings.scalePercent
    renderSettings.modificationListeners.add {
      val client = layoutInspector.currentClient
      if (client.inLiveMode) {
        client.updateScreenshotType(null, renderSettings.scaleFraction.toFloat())
      }
      if (prevZoom != renderSettings.scalePercent) {
        layoutInspector.coroutineScope.launch {
          floatingToolbarProvider.zoomChanged(prevZoom / 100.0, renderSettings.scalePercent / 100.0)
          prevZoom = renderSettings.scalePercent
          model.windows.values.forEach { it.refreshImages(renderSettings.scaleFraction) }
          contentPanel.renderModel.refresh()
        }
      }
    }
  }

  private fun updateLayeredPaneSize() {
    scrollPane.size = layeredPane.size
    val floatingToolbar = floatingToolbarProvider.floatingToolbar
    floatingToolbar.size = floatingToolbar.preferredSize
    floatingToolbar.location =
      Point(
        layeredPane.width - floatingToolbar.width - TOOLBAR_INSET,
        layeredPane.height - floatingToolbar.height - TOOLBAR_INSET,
      )
  }

  override fun zoom(type: ZoomType): Boolean {
    var newZoom = renderSettings.scalePercent
    viewportLayoutManager.currentZoomOperation = type
    when (type) {
      ZoomType.FIT -> newZoom = getFitZoom()
      ZoomType.ACTUAL -> newZoom = 100
      ZoomType.IN -> newZoom += 10
      ZoomType.OUT -> newZoom -= 10
    }
    newZoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    return if (newZoom != renderSettings.scalePercent) {
      renderSettings.scalePercent = newZoom
      contentPanel.revalidate()
      true
    } else {
      false
    }
  }

  private fun getFitZoom(): Int {
    val size = layoutInspector.inspectorModel.screenDimension
    val availableWidth = scrollPane.width - scrollPane.verticalScrollBar.width
    val availableHeight = scrollPane.height - scrollPane.horizontalScrollBar.height
    val desiredWidth = (size.width).toDouble()
    val desiredHeight = (size.height).toDouble()
    return if (desiredHeight == 0.0 || desiredWidth == 0.0) {
      100
    } else {
      (90 * min(availableHeight / desiredHeight, availableWidth / desiredWidth)).toInt()
    }
  }

  override fun canZoomIn() =
    renderSettings.scalePercent < MAX_ZOOM && !layoutInspector.inspectorModel.isEmpty

  override fun canZoomOut() =
    renderSettings.scalePercent > MIN_ZOOM && !layoutInspector.inspectorModel.isEmpty

  override fun canZoomToFit() =
    !layoutInspector.inspectorModel.isEmpty && getFitZoom() != renderSettings.scalePercent

  override fun canZoomToActual() =
    renderSettings.scalePercent < 100 && canZoomIn() ||
      renderSettings.scalePercent > 100 && canZoomOut()

  override fun uiDataSnapshot(sink: DataSink) {
    sink[ZOOMABLE_KEY] = this
    sink[PANNABLE_KEY] = this
    sink[TOGGLE_3D_ACTION_BUTTON_KEY] = floatingToolbarProvider.toggle3dActionButton
  }

  override val isPannable: Boolean
    get() =
      contentPanel.width > scrollPane.viewport.width ||
        contentPanel.height > scrollPane.viewport.height

  override var scrollPosition: Point
    get() = scrollPane.viewport.viewPosition
    set(_) {}

  private fun createToolbarPanel(actionToolbar: ActionToolbar): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border =
      BorderFactory.createMatteBorder(0, 0, 1, 0, com.android.tools.adtui.common.border)

    val leftPanel = AdtPrimaryPanel(BorderLayout())
    leftPanel.add(actionToolbar.component, BorderLayout.CENTER)
    panel.add(leftPanel, BorderLayout.CENTER)
    return panel
  }
}

@VisibleForTesting
class MyViewportLayoutManager(
  private val viewport: JViewport,
  private val layerSpacing: () -> Int,
  private val rootLocation: () -> Point?,
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
        val position =
          viewport.viewPosition.apply {
            translate(-viewport.view.width / 2, -viewport.view.height / 2)
          }
        origLayout.layoutContainer(parent)
        viewport.viewPosition =
          position.apply { translate(viewport.view.width / 2, viewport.view.height / 2) }
      }
      currentZoomOperation != null -> {
        viewport.viewPosition =
          when (currentZoomOperation) {
            ZoomType.FIT -> {
              origLayout.layoutContainer(parent)
              val bounds = viewport.extentSize
              val size = viewport.view.preferredSize
              Point(
                (size.width - bounds.width).coerceAtLeast(0) / 2,
                (size.height - bounds.height).coerceAtLeast(0) / 2,
              )
            }
            else -> {
              val position =
                SwingUtilities.convertPoint(
                  viewport,
                  Point(viewport.width / 2, viewport.height / 2),
                  viewport.view,
                )
              val xPercent = position.x.toDouble() / viewport.view.width.toDouble()
              val yPercent = position.y.toDouble() / viewport.view.height.toDouble()

              origLayout.layoutContainer(parent)

              val newPosition =
                Point(
                  (viewport.view.width * xPercent).toInt(),
                  (viewport.view.height * yPercent).toInt(),
                )
              newPosition.translate(-viewport.extentSize.width / 2, -viewport.extentSize.height / 2)
              newPosition
            }
          }
        currentZoomOperation = null
      }
      else -> {
        // Normal layout: Attempt to keep the image root location in place.
        origLayout.layoutContainer(parent)
        val lastRoot = lastRootLocation
        val currentRootLocation = rootLocation()
        val view = viewport.view
        if (view.size != lastViewSize && lastRoot != null && currentRootLocation != null) {
          val newRootLocation = SwingUtilities.convertPoint(view, currentRootLocation, viewport)
          val preferredSize = view.preferredSize
          val newPosition =
            viewport.viewPosition.apply {
              translate(newRootLocation.x - lastRoot.x, newRootLocation.y - lastRoot.y)
            }
          if (view.width > preferredSize.width) {
            // If there is room for the entire image set x position to 0 (required to remove the
            // horizontal scrollbar).
            newPosition.x = 0
          }
          if (view.height > preferredSize.height) {
            // If there is room for the entire image set y position to 0 (required to remove the
            // vertical scrollbar).
            newPosition.y = 0
          }
          viewport.viewPosition = newPosition
        }
      }
    }
    lastRootLocation =
      rootLocation()?.let { SwingUtilities.convertPoint(viewport.view, it, viewport) }
    lastViewSize = viewport.view.size
  }
}
