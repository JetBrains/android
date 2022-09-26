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

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.PANNABLE_KEY
import com.android.tools.adtui.Pannable
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.ui.ICON_EMULATOR
import com.android.tools.idea.appinspection.ide.ui.ICON_PHONE
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.appinspection.ide.ui.buildDeviceName
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.pipeline.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.ForegroundProcess
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.matchToProcessDescriptor
import com.android.tools.idea.layoutinspector.snapshots.CaptureSnapshotAction
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
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
import com.intellij.ui.LayeredIcon
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBLoadingPanelListener
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import icons.StudioIcons.LayoutInspector.LIVE_UPDATES
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
import java.util.concurrent.Executor
import java.util.concurrent.Executors.newSingleThreadExecutor
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

@VisibleForTesting
val ICON_LEGACY_PHONE = LayeredIcon(ICON_PHONE, AllIcons.General.WarningDecorator)

@VisibleForTesting
val ICON_LEGACY_EMULATOR = LayeredIcon(ICON_EMULATOR, AllIcons.General.WarningDecorator)

@TestOnly
const val DEVICE_VIEW_ACTION_TOOLBAR_NAME = "DeviceViewPanel.ActionToolbar"

const val PERFORMANCE_WARNING_3D = "performance.warning.3d"
const val PERFORMANCE_WARNING_HIDDEN = "performance.warning.hidden"

/**
 * Panel that shows the device screen in the layout inspector.
 *
 * @param onDeviceSelected is only invoked when [deviceModel] is used.
 */
class DeviceViewPanel(
  val deviceModel: DeviceModel?,
  val processesModel: ProcessesModel?,
  onDeviceSelected: (newDevice: DeviceDescriptor) -> Unit,
  onProcessSelected: (newProcess: ProcessDescriptor) -> Unit,
  val onStopInspector: () -> Unit,
  private val layoutInspector: LayoutInspector,
  private val viewSettings: DeviceViewSettings,
  disposableParent: Disposable,
  @TestOnly private val backgroundExecutor: Executor = AndroidExecutors.getInstance().workerThreadExecutor,
) : JPanel(BorderLayout()), Zoomable, DataProvider, Pannable {

  override val scale
    get() = viewSettings.scaleFraction

  override val screenScalingFactor = 1.0

  override var isPanning = false
    get() = ( field || isMiddleMousePressed || isSpacePressed ) && processesModel?.selectedProcess != null

  private var isSpacePressed = false
  private var isMiddleMousePressed = false
  private var lastPanMouseLocation: Point? = null
  private var performanceWarningGiven = false

  private val selectDeviceAction: SelectDeviceAction? = if (deviceModel != null) {
    SelectDeviceAction(
      deviceModel = deviceModel,
      onDeviceSelected = onDeviceSelected,
      onProcessSelected = onProcessSelected,
      onDetachAction = { stopInspectors() },
      customDeviceAttribution = ::deviceAttribution
    )
  }
  else {
    null
  }

  private val selectProcessAction: SelectProcessAction? = if (processesModel != null) {
    SelectProcessAction(
      model = processesModel,
      supportsOffline = false,
      createProcessLabel = (SelectProcessAction)::createCompactProcessLabel,
      stopPresentation = SelectProcessAction.StopPresentation(
        "Stop Inspector",
        "Stop running the layout inspector against the current process"),
      onStopAction = { stopInspectors() },
      customDeviceAttribution = ::deviceAttribution
    )
  }
  else {
    null
  }

  // TODO remove [selectedProcessAction] once the flag DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED is removed
  private val targetSelectedAction: DropDownActionWithButton? = if (
    StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED.get()
  ) {
    if (selectDeviceAction == null) {
      null
    }
    else {
      DropDownActionWithButton(selectDeviceAction) { selectDeviceAction.button }
    }
  }
  else {
    if (selectProcessAction == null) {
      null
    }
    else {
      DropDownActionWithButton(selectProcessAction) { selectProcessAction.button }
    }
  }

  private val contentPanel = DeviceViewContentPanel(
    inspectorModel = layoutInspector.layoutInspectorModel,
    deviceModel = deviceModel,
    treeSettings = layoutInspector.treeSettings,
    viewSettings = viewSettings,
    currentClient = { layoutInspector.currentClient },
    pannable = this,
    selectTargetAction = targetSelectedAction,
    disposableParent = disposableParent
  )

  private fun deviceAttribution(device: DeviceDescriptor, event: AnActionEvent) = when {
    device.apiLevel < AndroidVersion.VersionCodes.M -> {
      event.presentation.isEnabled = false
      event.presentation.text = "${device.buildDeviceName()} (Unsupported for API < ${AndroidVersion.VersionCodes.M})"
    }
    device.apiLevel < AndroidVersion.VersionCodes.Q -> {
      event.presentation.icon = device.toLegacyIcon()
      event.presentation.text = "${device.buildDeviceName()} (Live inspection disabled for API < ${AndroidVersion.VersionCodes.Q})"
    }
    else -> {
    }
  }

  private fun DeviceDescriptor?.toLegacyIcon() =
    if (this?.isEmulator == true) ICON_LEGACY_EMULATOR else ICON_LEGACY_PHONE

  private fun showGrab() {
    cursor = if (isPanning) {
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRAB)
    }
    else {
      Cursor.getDefaultCursor()
    }
  }

  private val panMouseListener: MouseAdapter = object : MouseAdapter() {
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
        lastPanMouseLocation = SwingUtilities.convertPoint(e.component, e.point, this@DeviceViewPanel)
        e.consume()
      }
    }

    override fun mouseDragged(e: MouseEvent) {
      val lastLocation = lastPanMouseLocation
      // convert to non-scrollable coordinates, otherwise as soon as the scroll is changed the mouse position also changes.
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
        cursor = if (isPanning) AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRAB) else Cursor.getDefaultCursor()
        lastPanMouseLocation = null
        e.consume()
      }
    }
  }

  private val scrollPane = JBScrollPane(contentPanel)
  private val layeredPane = JLayeredPane()
  private val loadingPane: JBLoadingPanel = JBLoadingPanel(BorderLayout(), disposableParent)
  private val deviceViewPanelActionsToolbar: DeviceViewPanelActionsToolbarProvider
  private val viewportLayoutManager = MyViewportLayoutManager(scrollPane.viewport, { contentPanel.renderModel.layerSpacing },
                                                              { contentPanel.rootLocation })

  private val actionToolbar: ActionToolbar = createToolbar(targetSelectedAction?.dropDownAction)

  /**
   * If the new [ForegroundProcess] is not debuggable (it's not present in [ProcessesModel]),
   * [DeviceViewContentPanel] will show an error message.
   */
  fun onNewForegroundProcess(foregroundProcess: ForegroundProcess) {
    if (processesModel == null) {
      contentPanel.showProcessNotDebuggableText = false
    }
    else {
      val processDescriptor = foregroundProcess.matchToProcessDescriptor(processesModel)
      contentPanel.showProcessNotDebuggableText = processDescriptor == null

      contentPanel.revalidate()
      contentPanel.repaint()
    }
  }

  init {
    loadingPane.addListener(object : JBLoadingPanelListener {
      override fun onLoadingStart() {
        contentPanel.showEmptyText = false
      }

      override fun onLoadingFinish() {
        contentPanel.showEmptyText = true
      }
    })

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
          showGrab()
        }
      }

      override fun keyReleased(e: KeyEvent) {
        if (e.keyCode == VK_SPACE) {
          isSpacePressed = false
          showGrab()
        }
      }
    })
    mouseListeners.forEach { contentPanel.addMouseListener(it) }
    mouseMotionListeners.forEach { contentPanel.addMouseMotionListener(it) }
    keyboardListeners.forEach { contentPanel.addKeyListener(it) }

    scrollPane.border = JBUI.Borders.empty()

    val toolbarComponent = createToolbarPanel(actionToolbar)
    add(toolbarComponent, BorderLayout.NORTH)
    loadingPane.add(layeredPane, BorderLayout.CENTER)
    add(loadingPane, BorderLayout.CENTER)
    val model = layoutInspector.layoutInspectorModel

    model.attachStageListeners.add { state ->
      val text = when (state) {
        DynamicLayoutInspectorErrorInfo.AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE -> "Unknown state"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.NOT_STARTED -> "Starting"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING -> "Adb ping success"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.ATTACH_SUCCESS -> "Attach success"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.START_REQUEST_SENT -> "Start request sent"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.START_RECEIVED -> "Start request received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.STARTED -> "Started"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.ROOTS_EVENT_SENT -> "Roots sent"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.ROOTS_EVENT_RECEIVED -> "Roots received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.VIEW_INVALIDATION_CALLBACK -> "Capture started"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.SCREENSHOT_CAPTURED -> "Screenshot captured"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.VIEW_HIERARCHY_CAPTURED -> "Hierarchy captured"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.RESPONSE_SENT -> "Response sent"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LAYOUT_EVENT_RECEIVED -> "View information received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.COMPOSE_REQUEST_SENT -> "Compose information request"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.COMPOSE_RESPONSE_RECEIVED -> "Compose information received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_WINDOW_LIST_REQUESTED -> "Legacy window list requested"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_WINDOW_LIST_RECEIVED -> "Legacy window list received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_HIERARCHY_REQUESTED -> "Legacy hierarchy requested"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_HIERARCHY_RECEIVED -> "Legacy hierarchy received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_SCREENSHOT_REQUESTED -> "Legacy screenshot requested"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_SCREENSHOT_RECEIVED -> "Legacy screenshot received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.PARSED_COMPONENT_TREE -> "Compose tree parsed"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.MODEL_UPDATED -> "Update complete"
      }

      if (text.isNotEmpty()) {
        loadingPane.setLoadingText(text)
      }
    }

    processesModel?.addSelectedProcessListeners(newSingleThreadExecutor()) {
      if (processesModel.selectedProcess?.isRunning == true) {
        if (model.isEmpty) {
          loadingPane.startLoading()
        }
      }
      if (processesModel.selectedProcess == null) {
          loadingPane.stopLoading()
      }
    }
    model.modificationListeners.add { old, new, _ ->
      if (old == null && new != null) {
        loadingPane.stopLoading()
      }
    }
    contentPanel.renderModel.modificationListeners.add {
      ApplicationManager.getApplication().invokeLater {
        actionToolbar.updateActionsImmediately()
        val performanceWarningNeeded = layoutInspector.currentClient.isCapturing && (contentPanel.renderModel.isRotated || model.hasHiddenNodes())
        if (performanceWarningNeeded != performanceWarningGiven) {
          if (performanceWarningNeeded) {
            when {
              contentPanel.renderModel.isRotated -> LayoutInspectorBundle.message(PERFORMANCE_WARNING_3D)
              model.hasHiddenNodes() -> LayoutInspectorBundle.message(PERFORMANCE_WARNING_HIDDEN)
              else -> null
            }?.let { InspectorBannerService.getInstance(model.project).setNotification(it) }
          }
          else {
            val service = InspectorBannerService.getInstance(model.project)
            service.removeNotification(LayoutInspectorBundle.message(PERFORMANCE_WARNING_3D))
            service.removeNotification(LayoutInspectorBundle.message(PERFORMANCE_WARNING_HIDDEN))
          }
        }
        performanceWarningGiven = performanceWarningNeeded
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
    layeredPane.add(scrollPane, BorderLayout.CENTER)

    // Zoom to fit on initial connect
    model.modificationListeners.add { _, new, _ ->
      if (contentPanel.renderModel.maxWidth == 0) {
        layoutInspector.currentClient.stats.recompositionHighlightColor = viewSettings.highlightColor
        contentPanel.renderModel.refresh()
        if (!zoom(ZoomType.FIT)) {
          // If we didn't change the zoom, we need to refresh explicitly. Otherwise the zoom listener will do it.
          new?.refreshImages(viewSettings.scaleFraction)
          contentPanel.renderModel.refresh()
        }
      }
      else {
        // refreshImages is done here instead of by the model itself so that we can be sure to zoom to fit first before trying to render
        // images upon first connecting.
        new?.refreshImages(viewSettings.scaleFraction)
        contentPanel.renderModel.refresh()
      }
    }
    var prevZoom = viewSettings.scalePercent
    viewSettings.modificationListeners.add {
      val client = layoutInspector.currentClient
      if (client.isCapturing) {
        client.updateScreenshotType(null, viewSettings.scaleFraction.toFloat())
      }
      if (prevZoom != viewSettings.scalePercent) {
        backgroundExecutor.execute {
          deviceViewPanelActionsToolbar.zoomChanged(prevZoom / 100.0, viewSettings.scalePercent / 100.0)
          prevZoom = viewSettings.scalePercent
          model.windows.values.forEach {
            it.refreshImages(viewSettings.scaleFraction)
          }
          contentPanel.renderModel.refresh()
        }
      }
    }
  }

  fun stopInspectors() {
    loadingPane.stopLoading()
    processesModel?.stop()
    onStopInspector.invoke()
  }

  private fun updateLayeredPaneSize() {
    scrollPane.size = layeredPane.size
    val floatingToolbar = deviceViewPanelActionsToolbar.floatingToolbar
    floatingToolbar.size = floatingToolbar.preferredSize
    floatingToolbar.location = Point(layeredPane.width - floatingToolbar.width - TOOLBAR_INSET,
                                     layeredPane.height - floatingToolbar.height - TOOLBAR_INSET)
  }

  override fun zoom(type: ZoomType): Boolean {
    var newZoom = viewSettings.scalePercent
    if (layoutInspector.layoutInspectorModel.isEmpty) {
      newZoom = 100
      scrollPane.viewport.revalidate()
    }
    else {
      viewportLayoutManager.currentZoomOperation = type
      when (type) {
        ZoomType.FIT -> newZoom = getFitZoom()
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

  private fun getFitZoom(): Int {
    val size = getScreenSize()
    val availableWidth = scrollPane.width - scrollPane.verticalScrollBar.width
    val availableHeight = scrollPane.height - scrollPane.horizontalScrollBar.height
    val desiredWidth = (size.width).toDouble()
    val desiredHeight = (size.height).toDouble()
    return if (desiredHeight == 0.0 || desiredWidth == 0.0) 100
    else (90 * min(availableHeight / desiredHeight, availableWidth / desiredWidth)).toInt()
  }

  private fun getScreenSize(): Dimension {
    // Use the screen size from the resource lookup if available.
    // This will make sure the screen size is correct even if there are windows we don't know about yet.
    // Example: If the initial screen has a dialog open, we may receive the dialog first. We do not want to zoom to fit the dialog size
    // since it is often smaller than the screen size.
    val size = layoutInspector.layoutInspectorModel.resourceLookup.screenDimension
    if (size.width > 0 && size.height > 0) {
      return size
    }
    // For the legacy inspector and for snapshots loaded from file, we do not have the screen size, but we know that all windows are loaded.
    val root = layoutInspector.layoutInspectorModel.root
    return Dimension(root.layoutBounds.width, root.layoutBounds.height)
  }

  override fun canZoomIn() = viewSettings.scalePercent < MAX_ZOOM && !layoutInspector.layoutInspectorModel.isEmpty

  override fun canZoomOut() = viewSettings.scalePercent > MIN_ZOOM && !layoutInspector.layoutInspectorModel.isEmpty

  override fun canZoomToFit() = !layoutInspector.layoutInspectorModel.isEmpty && getFitZoom() != viewSettings.scalePercent

  override fun canZoomToActual() = viewSettings.scalePercent < 100 && canZoomIn() || viewSettings.scalePercent > 100 && canZoomOut()

  override fun getData(dataId: String): Any? {
    if (ZOOMABLE_KEY.`is`(dataId) || PANNABLE_KEY.`is`(dataId)) {
      return this
    }
    if (DEVICE_VIEW_MODEL_KEY.`is`(dataId)) {
      return contentPanel.renderModel
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
  override var scrollPosition: Point
    get() = scrollPane.viewport.viewPosition
    set(_) {}

  private fun createToolbar(selectProcessAction: AnAction?): ActionToolbar {
    val leftGroup = DefaultActionGroup()
    selectProcessAction?.let { leftGroup.add(it) }
    leftGroup.add(Separator.getInstance())
    leftGroup.add(ViewMenuAction)
    leftGroup.add(ToggleOverlayAction)
    if (!layoutInspector.isSnapshot) {
      leftGroup.add(CaptureSnapshotAction)
    }
    leftGroup.add(AlphaSliderAction)
    if (!layoutInspector.isSnapshot) {
      leftGroup.add(Separator.getInstance())
      leftGroup.add(PauseLayoutInspectorAction)
      leftGroup.add(RefreshAction)
    }
    leftGroup.add(Separator.getInstance())
    leftGroup.add(LayerSpacingSliderAction)
    val actionToolbar = ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorLeft", leftGroup, true)
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
    actionToolbar.component.name = DEVICE_VIEW_ACTION_TOOLBAR_NAME
    actionToolbar.setTargetComponent(this)
    actionToolbar.updateActionsImmediately()
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

    @Suppress("DialogTitleCapitalization")
    override fun getTooltipLink(owner: JComponent?) = TooltipLinkProvider.TooltipLink("Learn More") {
      BrowserUtil.browse("https://d.android.com/r/studio-ui/layout-inspector-live-updates")
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
          ZoomType.FIT -> {
            origLayout.layoutContainer(parent)
            val bounds = viewport.extentSize
            val size = viewport.view.preferredSize
            Point((size.width - bounds.width).coerceAtLeast(0) / 2, (size.height - bounds.height).coerceAtLeast(0) / 2)
          }
          else -> {
            val position = SwingUtilities.convertPoint(viewport, Point(viewport.width / 2, viewport.height / 2), viewport.view)
            val xPercent = position.x.toDouble() / viewport.view.width.toDouble()
            val yPercent = position.y.toDouble() / viewport.view.height.toDouble()

            origLayout.layoutContainer(parent)

            val newPosition = Point((viewport.view.width * xPercent).toInt(), (viewport.view.height * yPercent).toInt())
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
          val newPosition = viewport.viewPosition.apply { translate(newRootLocation.x - lastRoot.x, newRootLocation.y - lastRoot.y) }
          if (view.width > preferredSize.width) {
            // If there is room for the entire image set x position to 0 (required to remove the horizontal scrollbar).
            newPosition.x = 0
          }
          if (view.height > preferredSize.height) {
            // If there is room for the entire image set y position to 0 (required to remove the vertical scrollbar).
            newPosition.y = 0
          }
          viewport.viewPosition = newPosition
        }
      }
    }
    lastRootLocation = rootLocation()?.let { SwingUtilities.convertPoint(viewport.view, it, viewport) }
    lastViewSize = viewport.view.size
  }
}
