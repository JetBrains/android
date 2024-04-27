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

import com.android.annotations.concurrency.AnyThread
import com.android.emulator.control.DisplayConfiguration
import com.android.emulator.control.DisplayConfigurations
import com.android.emulator.control.ExtendedControlsStatus
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.core.AbstractDisplayPanel
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.DisplayDescriptor
import com.android.tools.idea.streaming.core.LayoutNode
import com.android.tools.idea.streaming.core.LeafNode
import com.android.tools.idea.streaming.core.NUMBER_OF_DISPLAYS_KEY
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.core.PanelState
import com.android.tools.idea.streaming.core.STREAMING_SECONDARY_TOOLBAR_ID
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
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction
import com.android.utils.HashCodes
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
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
import java.awt.KeyboardFocusManager
import java.beans.PropertyChangeListener
import java.nio.file.Path
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
) : StreamingDevicePanel(DeviceId.ofEmulator(emulator.emulatorId), EMULATOR_MAIN_TOOLBAR_ID, STREAMING_SECONDARY_TOOLBAR_ID),
    ConnectionStateListener {

  private val displayPanels = Int2ObjectRBTreeMap<EmulatorDisplayPanel>()
  private val displayConfigurator = DisplayConfigurator()
  private var clipboardSynchronizer: EmulatorClipboardSynchronizer? = null
  private var contentDisposable: Disposable? = null

  override var primaryDisplayView: EmulatorView? = null

  private val multiDisplayStateStorage = MultiDisplayStateStorage.getInstance(project)
  private val multiDisplayStateUpdater = Runnable {
    multiDisplayStateStorage.setMultiDisplayState(emulatorId.avdFolder, displayConfigurator.getMultiDisplayState())
  }

  private val focusOwnerListener = PropertyChangeListener { event ->
    val newFocus = event.newValue
    val gained = newFocus is EmulatorView && newFocus.emulator == emulator
    val oldFocus = event.oldValue
    val lost = oldFocus is EmulatorView && oldFocus.emulator == emulator
    if (gained != lost) {
      if (gained) {
        if (connected && EmulatorSettings.getInstance().synchronizeClipboard) {
          clipboardSynchronizer?.setDeviceClipboardAndKeepHostClipboardInSync()
        }
      }
      else {
        clipboardSynchronizer?.stopKeepingHostClipboardInSync()
      }
    }
  }

  private val emulatorId
    get() = emulator.emulatorId

  override val title: String
    get() = emulatorId.avdName

  override val description: String
    get() = "${emulatorId.avdName} ${"(${emulatorId.serialNumber})".htmlColored(JBColor.GRAY)}"

  override val icon: Icon
    get() {
      val avd = AvdManagerConnection.getDefaultAvdManagerConnection().findAvd(emulatorId.avdId)
      val icon = avd?.icon ?: StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
      return ExecutionUtil.getLiveIndicator(icon)
    }

  override val isClosable: Boolean = true

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
  }

  override fun setDeviceFrameVisible(visible: Boolean) {
    primaryDisplayView?.deviceFrameVisible = visible
  }

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    if (connectionState == ConnectionState.CONNECTED) {
      displayConfigurator.refreshDisplayConfiguration()
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (isFocusOwner && EmulatorSettings.getInstance().synchronizeClipboard) {
          clipboardSynchronizer?.setDeviceClipboardAndKeepHostClipboardInSync()
        }
      }
    }
  }

  /**
   * Populates the emulator panel with content.
   */
  override fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState?) {
    if (contentDisposable != null) {
      thisLogger().error(IllegalStateException("${title}: content already exists"))
      return
    }

    lastUiState = null
    val disposable = Disposer.newDisposable()
    Disposer.register(this, disposable)
    contentDisposable = disposable

    clipboardSynchronizer = EmulatorClipboardSynchronizer(emulator, disposable)

    val primaryDisplayPanel =
        EmulatorDisplayPanel(disposable, emulator, PRIMARY_DISPLAY_ID, null, zoomToolbarVisible, deviceFrameVisible)
    displayPanels[primaryDisplayPanel.displayId] = primaryDisplayPanel
    val emulatorView = primaryDisplayPanel.displayView
    primaryDisplayView = emulatorView
    mainToolbar.targetComponent = emulatorView
    secondaryToolbar.targetComponent = emulatorView
    emulatorView.addPropertyChangeListener(DISPLAY_MODE_PROPERTY) {
      mainToolbar.updateActionsImmediately()
      secondaryToolbar.updateActionsImmediately()
    }
    installFileDropHandler(this, id.serialNumber, emulatorView, project)
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", focusOwnerListener)
    emulatorView.addDisplayConfigurationListener(displayConfigurator)
    emulatorView.addPostureListener(object: PostureListener {
      override fun postureChanged(posture: PostureDescriptor) {
        EventQueue.invokeLater {
          mainToolbar.updateActionsImmediately()
        }
      }
    })
    emulator.addConnectionStateListener(this)

    val multiDisplayState = multiDisplayStateStorage.getMultiDisplayState(emulatorId.avdFolder)
    if (multiDisplayState == null) {
      centerPanel.addToCenter(primaryDisplayPanel)
    }
    else {
      try {
        displayConfigurator.buildLayout(multiDisplayState)
      }
      catch (e: RuntimeException) {
        LOG.error("Corrupted multi-display state", e)
        // Corrupted multi-display state. Start with a single display.
        centerPanel.addToCenter(primaryDisplayPanel)
      }
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
    multiDisplayStateUpdater.run()
    multiDisplayStateStorage.removeUpdater(multiDisplayStateUpdater)

    val uiState = EmulatorUiState()

    for (panel in displayPanels.values) {
      uiState.zoomScrollState[panel.displayId] = panel.zoomScrollState
    }

    val manageSnapshotsDialog = primaryDisplayView?.let { findManageSnapshotDialog(it) }
    uiState.manageSnapshotsDialogShown = manageSnapshotsDialog != null
    manageSnapshotsDialog?.close(DialogWrapper.CLOSE_EXIT_CODE)

    if (connected) {
      emulator.closeExtendedControls(object: EmptyStreamObserver<ExtendedControlsStatus>() {
        override fun onNext(response: ExtendedControlsStatus) {
          EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
            uiState.extendedControlsShown = response.visibilityChanged
          }
        }
      })
    }

    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", focusOwnerListener)
    emulator.removeConnectionStateListener(this)
    contentDisposable?.let { Disposer.dispose(it) }
    contentDisposable = null

    centerPanel.removeAll()
    displayPanels.clear()
    primaryDisplayView = null
    mainToolbar.targetComponent = this
    secondaryToolbar.targetComponent = this
    clipboardSynchronizer = null
    lastUiState = uiState
    return uiState
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_CONTROLLER_KEY.name -> emulator
      EMULATOR_VIEW_KEY.name -> primaryDisplayView
      NUMBER_OF_DISPLAYS_KEY.name -> displayPanels.size
      ScreenRecorderAction.SCREEN_RECORDER_PARAMETERS_KEY.name -> getScreenRecorderParameters()
      else -> super.getData(dataId)
    }
  }

  private fun getScreenRecorderParameters(): ScreenRecorderAction.Parameters? {
    return if (emulator.connectionState == ConnectionState.CONNECTED) {
      ScreenRecorderAction.Parameters(emulatorId.avdName, id.serialNumber, emulator.emulatorConfig.api, emulatorId.avdId, emulator)
    }
    else {
      null
    }
  }

  private inner class DisplayConfigurator : DisplayConfigurationListener {

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
        override fun onNext(response: DisplayConfigurations) {
          LOG.debug("Display configurations: " + shortDebugString(response))
          EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
            displayConfigurationReceived(response.displaysList)
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
      mainToolbar.updateActionsImmediately()
      secondaryToolbar.updateActionsImmediately()
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
            EmulatorDisplayPanel(contentDisposable!!, emulator, it, display.size, zoomToolbarVisible)
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
          EmulatorDisplayPanel(contentDisposable!!, emulator, it, display.size, zoomToolbarVisible)
        })
      }
    }

    private fun setRootPanel(rootPanel: JPanel) {
      mainToolbar.updateActionsImmediately() // Rotation buttons are hidden in multi-display mode.
      secondaryToolbar.updateActionsImmediately()
      centerPanel.removeAll()
      centerPanel.addToCenter(rootPanel)
      centerPanel.validate()
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

    fun getMultiDisplayState(avdFolder: Path): MultiDisplayState? = displayStateByAvdFolder[avdFolder.toString()]

    fun setMultiDisplayState(avdFolder: Path, state: MultiDisplayState?) {
      if (state == null) {
        displayStateByAvdFolder.remove(avdFolder.toString())
      }
      else {
        displayStateByAvdFolder[avdFolder.toString()] = state
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
