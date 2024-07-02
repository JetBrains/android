/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.device

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.deviceprovisioner.DEVICE_HANDLE_KEY
import com.android.tools.idea.streaming.core.AbstractDisplayPanel
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.DisplayDescriptor
import com.android.tools.idea.streaming.core.LayoutNode
import com.android.tools.idea.streaming.core.LeafNode
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.core.PanelState
import com.android.tools.idea.streaming.core.STREAMING_SECONDARY_TOOLBAR_ID
import com.android.tools.idea.streaming.core.SplitNode
import com.android.tools.idea.streaming.core.SplitPanel
import com.android.tools.idea.streaming.core.StreamingDevicePanel
import com.android.tools.idea.streaming.core.computeBestLayout
import com.android.tools.idea.streaming.core.htmlColored
import com.android.tools.idea.streaming.core.installFileDropHandler
import com.android.tools.idea.streaming.core.sizeWithoutInsets
import com.android.tools.idea.streaming.device.DeviceView.ConnectionState
import com.android.tools.idea.streaming.device.DeviceView.ConnectionStateListener
import com.android.tools.idea.streaming.device.screenshot.DeviceScreenshotOptions
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction
import com.android.tools.idea.ui.screenshot.ScreenshotAction
import com.android.utils.HashCodes
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.util.concurrent.TimeoutException
import java.util.function.IntFunction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Provides view of one physical device in the Running Devices tool window.
 */
internal class DeviceToolWindowPanel(
  disposableParent: Disposable,
  private val project: Project,
  val deviceHandle: DeviceHandle,
  val deviceClient: DeviceClient,
) : StreamingDevicePanel(
    DeviceId.ofPhysicalDevice(deviceClient.deviceSerialNumber), DEVICE_MAIN_TOOLBAR_ID, STREAMING_SECONDARY_TOOLBAR_ID) {

  val deviceSerialNumber: String
    get() = deviceClient.deviceSerialNumber

  override val title: String
    get() = deviceClient.deviceName

  override val description: String
    get() {
      val properties = deviceClient.deviceConfig.deviceProperties
      val api = properties.androidVersion?.apiStringWithoutExtension ?: "${deviceClient.deviceConfig.apiLevel}"
      return "${properties.title} API $api ${"($deviceSerialNumber)".htmlColored(JBColor.GRAY)}"
    }

  override val icon: Icon
    get() = ExecutionUtil.getLiveIndicator(deviceClient.deviceConfig.deviceProperties.icon)

  override val isClosable: Boolean = true

  val component: JComponent
    get() = this

  override val preferredFocusableComponent: JComponent
    get() = primaryDisplayView ?: this

  override var zoomToolbarVisible = false
    set(value) {
      field = value
      for (displayPanel in displayPanels.values) {
        displayPanel.zoomToolbarVisible = value
      }
    }

  private var contentDisposable: Disposable? = null
  override var primaryDisplayView: DeviceView? = null
    private set
  private val deviceConfig
    get() = deviceClient.deviceConfig
  private val displayPanels = Int2ObjectRBTreeMap<DeviceDisplayPanel>()

  private val deviceStateListener = object : DeviceController.DeviceStateListener {
    override fun onSupportedDeviceStatesChanged(deviceStates: List<FoldingState>) {
      updateMainToolbarLater()
    }

    override fun onDeviceStateChanged(deviceState: Int) {
      updateMainToolbarLater()
    }

    private fun updateMainToolbarLater() {
      EventQueue.invokeLater {
        mainToolbar.updateActionsAsync()
      }
    }
  }

  init {
    Disposer.register(disposableParent, this)
  }

  override fun setDeviceFrameVisible(visible: Boolean) {
    // Showing device frame is not supported for physical devices.
  }

  /**
   * Populates the device panel with content.
   */
  override fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState?) {
    if (contentDisposable != null) {
      thisLogger().error(IllegalStateException("${title}: content already exists"))
      return
    }

    val disposable = Disposer.newDisposable()
    Disposer.register(this, disposable)
    contentDisposable = disposable

    val uiState = savedUiState as DeviceUiState? ?: DeviceUiState()
    val initialOrientation = uiState.orientation
    val primaryDisplayPanel =
        DeviceDisplayPanel(disposable, deviceClient, PRIMARY_DISPLAY_ID, initialOrientation, project, zoomToolbarVisible)
    displayPanels.put(primaryDisplayPanel.displayId, primaryDisplayPanel)
    val zoomScrollState = uiState.zoomScrollState
    for (displayPanel in displayPanels.values) {
      zoomScrollState[displayPanel.displayId]?.let { displayPanel.zoomScrollState = it }
    }

    val deviceView = primaryDisplayPanel.displayView
    primaryDisplayView = deviceView
    mainToolbar.targetComponent = deviceView
    secondaryToolbar.targetComponent = deviceView
    centerPanel.addToCenter(primaryDisplayPanel)
    val displayConfigurator = DisplayConfigurator()

    deviceView.addConnectionStateListener(object : ConnectionStateListener {
      @UiThread
      override fun connectionStateChanged(deviceSerialNumber: String, connectionState: ConnectionState) {
        when (connectionState) {
          ConnectionState.CONNECTED -> {
            deviceClient.deviceController?.apply {
              Disposer.register(disposable) {
                removeDisplayListener(displayConfigurator)
                removeDeviceStateListener(deviceStateListener)
              }
              addDisplayListener(displayConfigurator)
              displayConfigurator.onDisplaysChanged()
              addDeviceStateListener(deviceStateListener)
            }
          }
          ConnectionState.DISCONNECTED -> {
            deviceClient.deviceController?.apply {
              displayConfigurator.reconfigureDisplayPanels(emptyList())
              removeDisplayListener(displayConfigurator)
              removeDeviceStateListener(deviceStateListener)
            }
          }
          else -> {}
        }

        EventQueue.invokeLater {
          mainToolbar.updateActionsAsync()
          secondaryToolbar.updateActionsAsync()
        }
      }
    })

    installFileDropHandler(this, id.serialNumber, deviceView, project)
  }

  /**
   * Destroys content of the device panel and returns its state for later recreation.
   */
  override fun destroyContent(): DeviceUiState {
    val uiState = DeviceUiState()
    val disposable = contentDisposable ?: return uiState
    contentDisposable = null
    uiState.orientation = primaryDisplayView?.displayOrientationQuadrants ?: 0
    for (displayPanel in displayPanels.values) {
      uiState.zoomScrollState[displayPanel.displayId] = displayPanel.zoomScrollState
    }

    Disposer.dispose(disposable)

    centerPanel.removeAll()
    displayPanels.clear()
    primaryDisplayView = null
    mainToolbar.targetComponent = this
    secondaryToolbar.targetComponent = this
    return uiState
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      DEVICE_VIEW_KEY.name -> primaryDisplayView
      DEVICE_CLIENT_KEY.name -> deviceClient
      DEVICE_CONTROLLER_KEY.name -> deviceClient.deviceController
      DEVICE_HANDLE_KEY.name -> deviceHandle
      ScreenshotAction.SCREENSHOT_OPTIONS_KEY.name ->
          primaryDisplayView?.let { if (it.isConnected) DeviceScreenshotOptions(deviceSerialNumber, deviceConfig, it) else null }
      ScreenRecorderAction.SCREEN_RECORDER_PARAMETERS_KEY.name ->
          deviceClient.deviceController?.let {
            ScreenRecorderAction.Parameters(deviceClient.deviceName, deviceSerialNumber, deviceConfig.featureLevel, null, it)
          }
      else -> super.getData(dataId)
    }
  }

  private inner class DisplayConfigurator : DeviceController.DisplayListener {

    var displayDescriptors = emptyList<DisplayDescriptor>()

    @AnyThread
    override fun onDisplayAdded(displayId: Int) {
      onDisplaysChanged()
    }

    @AnyThread
    override fun onDisplayRemoved(displayId: Int) {
      onDisplaysChanged()
    }

    @AnyThread
    fun onDisplaysChanged() {
      contentDisposable?.let {
        AndroidCoroutineScope(it).launch {
          val displays = try {
            deviceClient.deviceController?.getDisplayConfigurations() ?: return@launch
          }
          catch (e: TimeoutException) {
            thisLogger().warn("Unable to get device display configurations", e)
            return@launch
          }
          if (displays.isEmpty()) {
            return@launch // All displays are turned off.
          }
          EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
            if (contentDisposable != null) {
              reconfigureDisplayPanels(displays)
            }
          }
        }
      }
    }

    fun reconfigureDisplayPanels(newDisplays: List<DisplayDescriptor>) {
      adjustDisplayDescriptors(newDisplays)
      if (newDisplays.size == 1 && displayDescriptors.size <= 1 || newDisplays == displayDescriptors) {
        return
      }

      val each = displayPanels.iterator()
      while (each.hasNext()) {
        val (displayId, displayPanel) = each.next()
        if (displayId != PRIMARY_DISPLAY_ID && !newDisplays.any { it.displayId == displayId }) {
          each.remove()
          Disposer.dispose(displayPanel)
        }
      }
      val layoutRoot = computeBestLayout(centerPanel.sizeWithoutInsets, newDisplays.map { it.size })
      val rootPanel = buildLayout(layoutRoot, newDisplays)
      displayDescriptors = newDisplays
      setRootPanel(rootPanel)
      mainToolbar.updateActionsAsync()
      secondaryToolbar.updateActionsAsync()
    }

    fun buildLayout(multiDisplayState: MultiDisplayState) {
      val newDisplays = multiDisplayState.displayDescriptors
      val rootPanel = buildLayout(multiDisplayState.panelState, newDisplays)
      displayDescriptors = newDisplays
      setRootPanel(rootPanel)
    }

    private fun buildLayout(layoutNode: LayoutNode, displayDescriptors: List<DisplayDescriptor>): JPanel {
      return when (layoutNode) {
        is LeafNode -> {
          val display = displayDescriptors[layoutNode.rectangleIndex]
          val displayId = display.displayId
          displayPanels.computeIfAbsent(displayId, IntFunction {
            assert(it != PRIMARY_DISPLAY_ID)
            DeviceDisplayPanel(contentDisposable!!, deviceClient, displayId, display.orientation, project, zoomToolbarVisible)
          })
        }
        is SplitNode -> {
          SplitPanel(layoutNode).apply {
            firstComponent = buildLayout(layoutNode.firstChild, displayDescriptors)
            secondComponent = buildLayout(layoutNode.secondChild, displayDescriptors)
          }
        }
      }
    }

    private fun buildLayout(state: PanelState, displayDescriptors: List<DisplayDescriptor>): JPanel {
      val splitPanelState = state.splitPanel
      return if (splitPanelState != null) {
        SplitPanel(splitPanelState.splitType, splitPanelState.proportion).apply {
          firstComponent = buildLayout(splitPanelState.firstComponent, displayDescriptors)
          secondComponent = buildLayout(splitPanelState.secondComponent, displayDescriptors)
        }
      }
      else {
        val displayId = state.displayId ?: throw IllegalArgumentException()
        val display = displayDescriptors.find { it.displayId == displayId } ?: throw IllegalArgumentException()
        displayPanels.computeIfAbsent(displayId, IntFunction {
          assert(it != PRIMARY_DISPLAY_ID)
          DeviceDisplayPanel(contentDisposable!!, deviceClient, displayId, display.orientation, project, zoomToolbarVisible)
        })
      }
    }

    private fun setRootPanel(rootPanel: JPanel) {
      mainToolbar.updateActionsAsync() // Rotation buttons are hidden in multi-display mode.
      secondaryToolbar.updateActionsAsync()
      centerPanel.removeAll()
      centerPanel.addToCenter(rootPanel)
      centerPanel.validate()
    }

    private fun adjustDisplayDescriptors(displays: List<DisplayDescriptor>) {
      for (display in displays) {
        val displayView = displayPanels[display.displayId]?.displayView ?: continue
        if (displayView.deviceDisplaySize.width != 0 && displayView.deviceDisplaySize.height != 0) {
          display.width = displayView.deviceDisplaySize.width
          display.height = displayView.deviceDisplaySize.height
          display.orientation = displayView.displayOrientationQuadrants
        }
      }
    }

    fun getMultiDisplayState(): MultiDisplayState? {
      if (centerPanel.componentCount > 0) {
        val panel = centerPanel.getComponent(0)
        if (panel is SplitPanel) {
          return MultiDisplayState(displayDescriptors.toMutableList(), panel.getState())
        }
      }
      return null
    }
  }

  /**
   * Persistent multi-display state corresponding to a single AVD.
   * The no-argument constructor is used by the XML deserializer.
   */
  class MultiDisplayState() {

    constructor(displayDescriptors: MutableList<DisplayDescriptor>, panelState: PanelState) : this() {
      this.displayDescriptors = displayDescriptors
      this.panelState = panelState
    }

    lateinit var displayDescriptors: MutableList<DisplayDescriptor>
    lateinit var panelState: PanelState

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as MultiDisplayState
      return displayDescriptors == other.displayDescriptors && panelState == other.panelState
    }

    override fun hashCode(): Int {
      return HashCodes.mix(displayDescriptors.hashCode(), panelState.hashCode())
    }
  }

  class DeviceUiState : UiState {
    var orientation = UNKNOWN_ORIENTATION
    val zoomScrollState = Int2ObjectRBTreeMap<AbstractDisplayPanel.ZoomScrollState>()
  }
}
