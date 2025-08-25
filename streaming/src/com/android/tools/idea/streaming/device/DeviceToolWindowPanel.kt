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

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.LocalEmulatorProperties
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.deviceprovisioner.DEVICE_HANDLE_KEY
import com.android.tools.idea.streaming.core.AbstractDisplayPanel
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.DisplayDescriptor
import com.android.tools.idea.streaming.core.DisplayType
import com.android.tools.idea.streaming.core.LayoutNode
import com.android.tools.idea.streaming.core.LeafNode
import com.android.tools.idea.streaming.core.PanelState
import com.android.tools.idea.streaming.core.SplitNode
import com.android.tools.idea.streaming.core.SplitPanel
import com.android.tools.idea.streaming.core.StreamingDevicePanel
import com.android.tools.idea.streaming.core.computeBestLayout
import com.android.tools.idea.streaming.core.htmlColored
import com.android.tools.idea.streaming.core.installFileDropHandler
import com.android.tools.idea.streaming.core.sizeWithoutInsets
import com.android.tools.idea.streaming.device.DeviceView.ConnectionState
import com.android.tools.idea.streaming.device.DeviceView.ConnectionStateListener
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction
import com.android.tools.idea.ui.screenrecording.ScreenRecordingParameters
import com.android.tools.idea.ui.screenshot.ScreenshotAction
import com.android.tools.idea.ui.screenshot.ScreenshotParameters
import com.android.utils.HashCodes
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
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
) : StreamingDevicePanel<DeviceDisplayPanel>(DeviceId.ofPhysicalDevice(deviceClient.deviceSerialNumber), DEVICE_MAIN_TOOLBAR_ID) {

  val deviceSerialNumber: String
    get() = deviceClient.deviceSerialNumber

  override val title: String
    get() = deviceClient.deviceName

  override val description: String
    get() {
      val properties = deviceConfig.deviceProperties
      val api = properties.androidVersion?.apiStringWithoutExtension ?: "${deviceConfig.apiLevel}"
      return "${properties.title} API $api ${"($deviceSerialNumber)".htmlColored(JBColor.GRAY)}"
    }

  override val icon: Icon
    get() = ExecutionUtil.getLiveIndicator(deviceConfig.deviceProperties.icon)

  override val deviceType: DeviceType
    get() = deviceConfig.deviceType

  private val deviceConfig: DeviceConfiguration
    get() = deviceClient.deviceConfig

  private val deviceController
    get() = deviceClient.deviceController

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

  private val deviceStateListener = object : DeviceController.DeviceStateListener {
    override fun onSupportedDeviceStatesChanged(deviceStates: List<FoldingState>) {
      ActivityTracker.getInstance().inc()
    }

    override fun onDeviceStateChanged(deviceState: Int) {
      ActivityTracker.getInstance().inc()
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
            deviceController?.apply {
              Disposer.register(disposable) {
                removeDisplayListener(displayConfigurator)
                removeDeviceStateListener(deviceStateListener)
              }
              addDisplayListener(displayConfigurator)
              displayConfigurator.initialize()
              addDeviceStateListener(deviceStateListener)
            }

            showContextMenuAdvertisementIfNecessary(disposable)
          }
          ConnectionState.DISCONNECTED -> {
            deviceController?.apply {
              displayConfigurator.reconfigureDisplayPanels(emptyList())
              removeDisplayListener(displayConfigurator)
              removeDeviceStateListener(deviceStateListener)
            }
          }
          else -> {}
        }

        ActivityTracker.getInstance().inc()
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

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[DEVICE_VIEW_KEY] = primaryDisplayView
    sink[DEVICE_CLIENT_KEY] = deviceClient
    sink[DEVICE_CONTROLLER_KEY] = deviceController
    sink[DEVICE_HANDLE_KEY] = deviceHandle
    sink[ScreenshotAction.SCREENSHOT_PARAMETERS_KEY] = deviceController?.let { createScreenshotOptions() }
    sink[ScreenRecorderAction.SCREEN_RECORDER_PARAMETERS_KEY] = deviceController?.let { createScreenRecorderParameters(it) }
  }

  private fun createScreenshotOptions(): ScreenshotParameters {
    val properties = deviceConfig.deviceProperties
    return when (properties) {
      is LocalEmulatorProperties -> ScreenshotParameters(deviceSerialNumber, deviceConfig.deviceType, properties.avdPath)
      else -> ScreenshotParameters(deviceSerialNumber, deviceConfig.deviceType, deviceConfig.deviceModel)
    }
  }

  private fun createScreenRecorderParameters(deviceController: DeviceController): ScreenRecordingParameters =
      ScreenRecordingParameters(deviceSerialNumber, deviceClient.deviceName, deviceConfig.featureLevel, deviceController, null)

  private inner class DisplayConfigurator : DeviceController.DisplayListener {

    /** Display descriptors sorted by display ID. */
    var displayDescriptors: List<DisplayDescriptor> = displayPanels.values.map { DisplayDescriptor(it.displayId, 0, 0) }

    @AnyThread
    fun initialize() {
      contentDisposable?.let {
        it.createCoroutineScope().launch {
          val displays = try {
            deviceController?.getDisplayConfigurations() ?: return@launch
          }
          catch (_: TimeoutException) {
            thisLogger().warn("Timed out waiting for display configurations from ${deviceClient.deviceName}")
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

    @AnyThread
    override fun onDisplayAddedOrChanged(displayId: Int, width: Int, height: Int, rotation: Int, displayType: DisplayType) {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (contentDisposable != null) {
          val newDisplays = displayDescriptors.toMutableList()
          val pos = newDisplays.binarySearch { it.displayId.compareTo(displayId) }
          if (pos >= 0) {
            newDisplays[pos].width = width
            newDisplays[pos].height = height
            newDisplays[pos].orientation = rotation
            newDisplays[pos].type = displayType
          }
          else {
            newDisplays.add(pos.inv(), DisplayDescriptor(displayId, width, height, rotation, displayType))
          }
          reconfigureDisplayPanels(newDisplays)
        }
      }
    }

    @AnyThread
    override fun onDisplayRemoved(displayId: Int) {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (contentDisposable != null) {
          if (displayDescriptors.find { it.displayId == displayId } != null) {
            val newDisplays = displayDescriptors.filterTo(mutableListOf()) { it.displayId != displayId }
            reconfigureDisplayPanels(newDisplays)
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
      ActivityTracker.getInstance().inc()
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
      centerPanel.removeAll()
      centerPanel.addToCenter(rootPanel)
      centerPanel.validate()
      ActivityTracker.getInstance().inc()
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
          return MultiDisplayState(displayDescriptors, panel.getState())
        }
      }
      return null
    }
  }

  /**
   * Persistent multi-display state corresponding to a single device.
   * The no-argument constructor is used by the XML deserializer.
   */
  class MultiDisplayState() {

    constructor(displayDescriptors: Collection<DisplayDescriptor>, panelState: PanelState) : this() {
      this.displayDescriptors = displayDescriptors.toMutableList()
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
