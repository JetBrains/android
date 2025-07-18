/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.annotations.concurrency.AnyThread
import com.android.emulator.control.DisplayConfiguration
import com.android.emulator.control.DisplayConfigurations
import com.android.emulator.control.ExtendedControlsStatus
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.core.AbstractDisplayPanel
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.DisplayDescriptor
import com.android.tools.idea.streaming.core.LayoutNode
import com.android.tools.idea.streaming.core.LeafNode
import com.android.tools.idea.streaming.core.NUMBER_OF_DISPLAYS_KEY
import com.android.tools.idea.streaming.core.PanelState
import com.android.tools.idea.streaming.core.SplitNode
import com.android.tools.idea.streaming.core.SplitPanel
import com.android.tools.idea.streaming.core.StreamingDevicePanel
import com.android.tools.idea.streaming.core.computeBestLayout
import com.android.tools.idea.streaming.core.htmlColored
import com.android.tools.idea.streaming.core.icon
import com.android.tools.idea.streaming.core.installFileDropHandler
import com.android.tools.idea.streaming.core.sizeWithoutInsets
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration.PostureDescriptor
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.streaming.emulator.actions.findManageSnapshotDialog
import com.android.tools.idea.streaming.emulator.actions.showExtendedControls
import com.android.tools.idea.streaming.emulator.actions.showManageSnapshotsDialog
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction.Companion.SCREEN_RECORDER_PARAMETERS_KEY
import com.android.tools.idea.ui.screenrecording.ScreenRecordingParameters
import com.android.utils.HashCodes
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Property
import icons.StudioIcons
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap
import org.jetbrains.annotations.TestOnly
import java.awt.EventQueue
import java.util.function.IntFunction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

private val LOG get() = Logger.getInstance(EmulatorToolWindowPanel::class.java)

/**
 * Provides view of one AVD in the Running Devices tool window.
 */
internal class EmulatorToolWindowPanel(
  disposableParent: Disposable,
  private val project: Project,
  val emulator: EmulatorController
) : StreamingDevicePanel<EmulatorDisplayPanel>(DeviceId.ofEmulator(emulator.emulatorId), EMULATOR_MAIN_TOOLBAR_ID),
    ConnectionStateListener {

  private val displayConfigurator = DisplayConfigurator(project)
  private var contentDisposable: Disposable? = null

  override var primaryDisplayView: EmulatorView? = null

  private val multiDisplayStateStorage = MultiDisplayStateStorage.getInstance(project)
  private val multiDisplayStateUpdater = Runnable {
    multiDisplayStateStorage.setMultiDisplayState(emulatorId.avdId, displayConfigurator.getMultiDisplayState())
  }

  private val emulatorId
    get() = emulator.emulatorId

  override val title: String
    get() {
      val avdName = emulatorId.avdName
      if (avdName.contains(" API ")) {
        return avdName
      }
      return "$avdName API ${emulator.emulatorConfig.androidVersion.apiStringWithoutExtension}"
    }

  override val description: String
    get() = "$title ${"(${emulatorId.serialNumber})".htmlColored(JBColor.GRAY)}"

  override val icon: Icon
    get() {
      val avd = AvdManagerConnection.getDefaultAvdManagerConnection().findAvdWithFolder(emulatorId.avdFolder)
      val icon = avd?.icon ?: StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
      return ExecutionUtil.getLiveIndicator(icon)
    }

  override val deviceType: DeviceType
    get() = emulator.emulatorConfig.deviceType

  override val preferredFocusableComponent: JComponent
    get() = primaryDisplayView ?: this

  override var zoomToolbarVisible = false
    set(value) {
      field = value
      for (panel in displayPanels.values) {
        panel.zoomToolbarVisible = value
      }
    }

  private val connected
    get() = emulator.connectionState == ConnectionState.CONNECTED

  @get:TestOnly
  var lastUiState: EmulatorUiState? = null

  init {
    Disposer.register(disposableParent, this)

    // Start Adb ready service for context menu actions.
    project.service<EmulatorAdbReadyService>()
  }

  override fun setDeviceFrameVisible(visible: Boolean) {
    primaryDisplayView?.deviceFrameVisible = visible
  }

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    if (connectionState == ConnectionState.CONNECTED) {
      displayConfigurator.refreshDisplayConfiguration()

      showContextMenuAdvertisementIfNecessary(contentDisposable!!)
    }

    ActivityTracker.getInstance().inc()
  }

  /**
   * Populates the emulator panel with content.
   */
  override fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState?) {
    if (contentDisposable != null) {
      LOG.error(IllegalStateException("$title: content already exists"))
      return
    }

    lastUiState = null
    val disposable = Disposer.newDisposable()
    Disposer.register(this, disposable)
    contentDisposable = disposable

    val primaryDisplayPanel =
        EmulatorDisplayPanel(disposable, emulator, project, PRIMARY_DISPLAY_ID, null, zoomToolbarVisible, deviceFrameVisible)
    displayPanels[primaryDisplayPanel.displayId] = primaryDisplayPanel
    val emulatorView = primaryDisplayPanel.displayView
    primaryDisplayView = emulatorView
    installFileDropHandler(this, id.serialNumber, emulatorView, project)
    emulatorView.addDisplayConfigurationListener(displayConfigurator)
    emulatorView.addPostureListener(object: PostureListener {
      override fun postureChanged(posture: PostureDescriptor) {
        ActivityTracker.getInstance().inc()
      }
    })
    emulator.addConnectionStateListener(this)

    val multiDisplayState = multiDisplayStateStorage.getMultiDisplayState(emulatorId.avdId)
    if (multiDisplayState?.isInitialized() == true) {
      try {
        displayConfigurator.buildLayout(multiDisplayState)
      }
      catch (e: RuntimeException) {
        LOG.error("Corrupted multi-display state", e)
        // Corrupted multi-display state. Start with a single display.
        centerPanel.addToCenter(primaryDisplayPanel)
      }
    }
    else {
      centerPanel.addToCenter(primaryDisplayPanel)
    }

    mainToolbar.targetComponent = emulatorView
    secondaryToolbar.targetComponent = emulatorView
    emulatorView.addPropertyChangeListener(DISPLAY_MODE_PROPERTY) {
      ActivityTracker.getInstance().inc()
    }

    val uiState = savedUiState as EmulatorUiState? ?: EmulatorUiState()
    val zoomScrollState = uiState.zoomScrollState
    for (panel in displayPanels.values) {
      zoomScrollState[panel.displayId]?.let { panel.zoomScrollState = it }
    }

    multiDisplayStateStorage.addUpdater(multiDisplayStateUpdater)

    if (connected) {
      displayConfigurator.refreshDisplayConfiguration()

      if (uiState.manageSnapshotsDialogShown) {
        showManageSnapshotsDialog(emulatorView, project)
      }
      if (uiState.extendedControlsShown) {
        showExtendedControls(emulator, project)
      }
    }
  }

  /**
   * Destroys content of the emulator panel and returns its state for later recreation.
   */
  override fun destroyContent(): EmulatorUiState {
    val uiState = EmulatorUiState()
    val disposable = contentDisposable ?: return uiState
    contentDisposable = null

    multiDisplayStateUpdater.run()
    multiDisplayStateStorage.removeUpdater(multiDisplayStateUpdater)

    for (panel in displayPanels.values) {
      uiState.zoomScrollState[panel.displayId] = panel.zoomScrollState
    }

    val manageSnapshotsDialog = primaryDisplayView?.let { findManageSnapshotDialog(it) }
    uiState.manageSnapshotsDialogShown = manageSnapshotsDialog != null
    manageSnapshotsDialog?.close(DialogWrapper.CLOSE_EXIT_CODE)

    if (connected) {
      emulator.closeExtendedControls(object: EmptyStreamObserver<ExtendedControlsStatus>() {
        override fun onNext(message: ExtendedControlsStatus) {
          EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
            uiState.extendedControlsShown = message.visibilityChanged
          }
        }
      })
    }

    emulator.removeConnectionStateListener(this)
    Disposer.dispose(disposable)

    centerPanel.removeAll()
    displayPanels.clear()
    primaryDisplayView = null
    mainToolbar.targetComponent = this
    secondaryToolbar.targetComponent = this
    lastUiState = uiState
    return uiState
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[EMULATOR_CONTROLLER_KEY] = emulator
    sink[EMULATOR_VIEW_KEY] = primaryDisplayView
    sink[NUMBER_OF_DISPLAYS_KEY] = displayPanels.size
    sink[SCREEN_RECORDER_PARAMETERS_KEY] = getScreenRecorderParameters()
  }

  private fun getScreenRecorderParameters(): ScreenRecordingParameters? {
    return if (emulator.connectionState == ConnectionState.CONNECTED) {
      ScreenRecordingParameters(emulatorId.serialNumber, emulatorId.avdName, emulator.emulatorConfig.api, emulator, emulatorId.avdFolder)
    }
    else {
      null
    }
  }

  private inner class DisplayConfigurator(private val project: Project) : DisplayConfigurationListener {

    var displayDescriptors = emptyList<DisplayDescriptor>()

    @AnyThread
    override fun displayConfigurationChanged(displayConfigs: List<DisplayConfiguration>?) {
      if (displayConfigs == null) {
        refreshDisplayConfiguration()
      }
      else {
        displayConfigurationReceived(displayConfigs)
      }
    }

    @AnyThread
    fun refreshDisplayConfiguration() {
      emulator.getDisplayConfigurations(object : EmptyStreamObserver<DisplayConfigurations>() {
        override fun onNext(message: DisplayConfigurations) {
          if (StudioFlags.EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
            LOG.info("Display configurations: " + shortDebugString(message))
          }
          else {
            LOG.debug("Display configurations: " + shortDebugString(message))
          }
          EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
            displayConfigurationReceived(message.displaysList)
          }
        }
      })
    }

    private fun displayConfigurationReceived(displayConfigs: List<DisplayConfiguration>) {
      val primaryDisplayView = primaryDisplayView ?: return
      val newDisplays = getDisplayDescriptors(primaryDisplayView, displayConfigs)
      if (newDisplays.size == 1 && displayDescriptors.size <= 1 || newDisplays == displayDescriptors) {
        return
      }

      displayPanels.int2ObjectEntrySet().removeIf { (displayId, displayPanel) ->
        if (!newDisplays.any { it.displayId == displayId }) {
          Disposer.dispose(displayPanel)
          true
        }
        else {
          false
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
            EmulatorDisplayPanel(contentDisposable!!, emulator, project, it, display.size, zoomToolbarVisible)
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
          EmulatorDisplayPanel(contentDisposable!!, emulator, project, it, display.size, zoomToolbarVisible)
        })
      }
    }

    private fun setRootPanel(rootPanel: JPanel) {
      centerPanel.removeAll()
      centerPanel.addToCenter(rootPanel)
      centerPanel.validate()

      // Toolbar updates should be requested after all AbstractDisplayView have been placed in component hierarchy.
      // Otherwise, we risk the action update to have a partial component hierarchy which may lead to wrong results.
      // See b/351129848
      ActivityTracker.getInstance().inc()
    }

    private fun getDisplayDescriptors(emulatorView: EmulatorView, displays: List<DisplayConfiguration>): List<DisplayDescriptor> {
      return displays
        .map {
          if (it.display == PRIMARY_DISPLAY_ID) {
            DisplayDescriptor(PRIMARY_DISPLAY_ID, emulatorView.displaySizeWithFrame)
          }
          else {
            it.toDisplayDescriptor()
          }
        }
        .sorted()
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

  class EmulatorUiState : UiState {
    var manageSnapshotsDialogShown = false
    var extendedControlsShown = false
    val zoomScrollState = Int2ObjectRBTreeMap<AbstractDisplayPanel.ZoomScrollState>()
  }

  /**
   * Persistent multi-display state corresponding to a single AVD.
   * The no-argument constructor is used by the XML deserializer.
   */
  class MultiDisplayState() {

    lateinit var displayDescriptors: MutableList<DisplayDescriptor>
    lateinit var panelState: PanelState

    constructor(displayDescriptors: MutableList<DisplayDescriptor>, panelState: PanelState) : this() {
      this.displayDescriptors = displayDescriptors
      this.panelState = panelState
    }

    fun isInitialized(): Boolean = ::displayDescriptors.isInitialized && ::panelState.isInitialized

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

  @Service(Service.Level.PROJECT)
  @State(name = "EmulatorDisplays", storages = [Storage("emulatorDisplays.xml")])
  class MultiDisplayStateStorage : PersistentStateComponent<MultiDisplayStateStorage> {

    @get:Property(surroundWithTag = true)
    var displayStateByAvdFolder = mutableMapOf<String, MultiDisplayState>()

    private val updaters = mutableListOf<Runnable>()

    override fun getState(): MultiDisplayStateStorage {
      for (updater in updaters) {
        updater.run()
      }
      return this
    }

    override fun loadState(state: MultiDisplayStateStorage) {
      XmlSerializerUtil.copyBean(state, this)
    }

    fun addUpdater(updater: Runnable) {
      updaters.add(updater)
    }

    fun removeUpdater(updater: Runnable) {
      updaters.remove(updater)
    }

    fun getMultiDisplayState(avdId: String): MultiDisplayState? = displayStateByAvdFolder[avdId]

    fun setMultiDisplayState(avdId: String, state: MultiDisplayState?) {
      if (state == null) {
        displayStateByAvdFolder.remove(avdId)
      }
      else {
        displayStateByAvdFolder[avdId] = state
      }
    }

    companion object {
      @JvmStatic
      fun getInstance(project: Project): MultiDisplayStateStorage {
        return project.getService(MultiDisplayStateStorage::class.java)
      }
    }
  }
}

private fun DisplayConfiguration.toDisplayDescriptor(): DisplayDescriptor =
    DisplayDescriptor(display, width, height)
