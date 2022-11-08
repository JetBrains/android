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
package com.android.tools.idea.emulator

import com.android.adblib.DeviceInfo
import com.android.adblib.DevicePropertyNames
import com.android.adblib.DevicePropertyNames.RO_BOOT_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_BUILD_CHARACTERISTICS
import com.android.adblib.DevicePropertyNames.RO_KERNEL_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABI
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.deviceProperties
import com.android.adblib.trackDevices
import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.device.DeviceToolWindowPanel
import com.android.tools.idea.device.dialogs.MirroringConfirmationDialog
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.emulator.RunningDevicePanel.UiState
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.google.common.cache.CacheBuilder
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.actions.ToggleToolbarAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil.createBoundedApplicationPoolExecutor
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.apache.commons.lang.WordUtils
import java.awt.EventQueue
import java.text.Collator
import java.time.Duration
import java.util.concurrent.CancellationException

/**
 * Manages contents of the Emulator tool window. Listens to changes in [RunningEmulatorCatalog]
 * and maintains [EmulatorToolWindowPanel]s, one per running Emulator.
 */
@UiThread
internal class EmulatorToolWindowManager @AnyThread private constructor(
  private val project: Project
) : RunningEmulatorCatalog.Listener, DeviceMirroringSettingsListener, DumbAware {

  private val deviceMirroringSettings = DeviceMirroringSettings.getInstance()
  private var contentCreated = false
  private var physicalDeviceWatcher: PhysicalDeviceWatcher? = null
  private val panels = arrayListOf<RunningDevicePanel>()
  private var selectedPanel: RunningDevicePanel? = null
  /** When the tool window is hidden, the ID of the last selected device, otherwise null. */
  private var lastSelectedDeviceId: DeviceId? = null
  /** When the tool window is hidden, the state of the UI for all emulators, otherwise empty. */
  private val savedUiState = hashMapOf<DeviceId, UiState>()
  private val emulators = hashSetOf<EmulatorController>()
  /** Properties of mirrorable devices keyed by serial numbers. */
  private var mirrorableDeviceProperties = mutableMapOf<String, Map<String, String>>()
  /** Serial numbers of mirrored devices. */
  private var mirroredDevices = mutableSetOf<String>()
  private val properties = PropertiesComponent.getInstance(project)
  // Serial numbers of devices that recently requested attention.
  private val recentAttentionRequests = CacheBuilder.newBuilder().expireAfterWrite(ATTENTION_REQUEST_EXPIRATION).build<String, String>()
  // IDs of recently launched AVDs keyed by themselves.
  private val recentEmulatorLaunches = CacheBuilder.newBuilder().expireAfterWrite(ATTENTION_REQUEST_EXPIRATION).build<String, String>()
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project.earlyDisposable)

  private val contentManagerListener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      viewSelectionChanged(getToolWindow())
    }

    override fun contentRemoveQuery(event: ContentManagerEvent) {
      val panel = event.content.component as? RunningDevicePanel ?: return
      if (panel is EmulatorToolWindowPanel) {
        panel.emulator.shutdown()
      }

      panels.remove(panel)
      savedUiState.remove(panel.id)
      if (panels.isEmpty()) {
        createEmptyStatePanel()
        hideLiveIndicator(getToolWindow())
      }
    }
  }

  private val connectionStateListener = object : ConnectionStateListener {
    @AnyThread
    override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
      if (connectionState == ConnectionState.DISCONNECTED) {
        EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
          if (contentCreated && emulators.remove(emulator)) {
            removeEmulatorPanel(emulator)
          }
        }
      }
    }
  }

  private var deviceFrameVisible
    get() = properties.getBoolean(DEVICE_FRAME_VISIBLE_PROPERTY, DEVICE_FRAME_VISIBLE_DEFAULT)
    set(value) {
      properties.setValue(DEVICE_FRAME_VISIBLE_PROPERTY, value, DEVICE_FRAME_VISIBLE_DEFAULT)
      for (panel in panels) {
        panel.setDeviceFrameVisible(value)
      }
    }

  private var zoomToolbarIsVisible
    get() = properties.getBoolean(ZOOM_TOOLBAR_VISIBLE_PROPERTY, ZOOM_TOOLBAR_VISIBLE_DEFAULT)
    set(value) {
      properties.setValue(ZOOM_TOOLBAR_VISIBLE_PROPERTY, value, ZOOM_TOOLBAR_VISIBLE_DEFAULT)
      for (panel in panels) {
        panel.zoomToolbarVisible = value
      }
    }

  init {
    Disposer.register(project.earlyDisposable) {
      ToolWindowManager.getInstance(project).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID)?.let { destroyContent(it) }
    }

    // Lazily initialize content since we can only have one frame.
    val messageBusConnection = project.messageBus.connect(project.earlyDisposable)
    messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return

        toolWindowManager.invokeLater {
          if (!project.isDisposed) {
            if (toolWindow.isVisible) {
              createContent(toolWindow)
            }
            else {
              destroyContent(toolWindow)
            }
          }
        }
      }
    })

    messageBusConnection.subscribe(AvdLaunchListener.TOPIC,
                                   AvdLaunchListener { avd, commandLine, project ->
                                     if (project == this.project && isEmbeddedEmulator(commandLine)) {
                                       RunningEmulatorCatalog.getInstance().updateNow()
                                       EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
                                         onEmulatorHeadsUp(avd.name)
                                       }
                                     }
                                   })

    messageBusConnection.subscribe(DeviceHeadsUpListener.TOPIC,
                                   DeviceHeadsUpListener { deviceSerialNumber, project ->
                                     if (project == this.project) {
                                       UIUtil.invokeLaterIfNeeded {
                                         onDeviceHeadsUp(deviceSerialNumber)
                                       }
                                     }
                                   })

    messageBusConnection.subscribe(DeviceMirroringSettingsListener.TOPIC, this)

    if (deviceMirroringSettings.deviceMirroringEnabled) {
      UIUtil.invokeLaterIfNeeded {
        createPhysicalDeviceWatcherIfToolWindowAvailable(ToolWindowManager.getInstance(project))
        if (physicalDeviceWatcher == null) {
          messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {

            override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
              if (ids.contains(RUNNING_DEVICES_TOOL_WINDOW_ID) && deviceMirroringSettings.deviceMirroringEnabled &&
                  physicalDeviceWatcher == null) {
                createPhysicalDeviceWatcherIfToolWindowAvailable(toolWindowManager)
              }
            }
          })
        }
      }
    }
  }

  private fun createPhysicalDeviceWatcherIfToolWindowAvailable(toolWindowManager: ToolWindowManager) {
    val toolWindow = toolWindowManager.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID)
    if (toolWindow != null) {
      physicalDeviceWatcher = PhysicalDeviceWatcher(toolWindow.disposable)
    }
  }

  private fun onDeviceHeadsUp(deviceSerialNumber: String) {
    if (mirrorableDeviceProperties.contains(deviceSerialNumber)) {
      onPhysicalDeviceHeadsUp(deviceSerialNumber)
    }
    else {
      recentAttentionRequests.put(deviceSerialNumber, deviceSerialNumber)
      alarm.addRequest(recentAttentionRequests::cleanUp, ATTENTION_REQUEST_EXPIRATION.toMillis())
      if (deviceSerialNumber.startsWith("emulator-")) {
        val future = RunningEmulatorCatalog.getInstance().updateNow()
        future.addCallback(EdtExecutorService.getInstance(),
                           success = { emulators ->
                             if (emulators != null) {
                               onEmulatorHeadsUp(deviceSerialNumber, emulators)
                             }
                           },
                           failure = {})
      }
    }
  }

  private fun onPhysicalDeviceHeadsUp(deviceSerialNumber: String) {
    val toolWindow = getToolWindow()
    if (!toolWindow.isVisible) {
      lastSelectedDeviceId = DeviceId.ofPhysicalDevice(deviceSerialNumber)
      toolWindow.showAndActivate()
    }
    else {
      val panel = findPanelBySerialNumber(deviceSerialNumber)
      if (panel != null) {
        selectPanel(panel, toolWindow)
        toolWindow.showAndActivate()
      }
    }
  }

  private fun onEmulatorHeadsUp(deviceSerialNumber: String, runningEmulators: Set<EmulatorController>) {
    val emulator = runningEmulators.find { it.emulatorId.serialNumber == deviceSerialNumber } ?: return
    // Ignore standalone emulators.
    if (emulator.emulatorId.isEmbedded) {
      onEmulatorHeadsUp(emulator.emulatorId.avdId)
    }
  }

  private fun onEmulatorHeadsUp(avdId: String) {
    val toolWindow = getToolWindow()
    toolWindow.showAndActivate()

    val panel = findPanelByAvdId(avdId)
    if (panel == null) {
      RunningEmulatorCatalog.getInstance().updateNow()
      recentEmulatorLaunches.put(avdId, avdId)
      alarm.addRequest(recentEmulatorLaunches::cleanUp, ATTENTION_REQUEST_EXPIRATION.toMillis())
    }
    else {
      selectPanel(panel, toolWindow)
    }
  }

  private fun selectPanel(panel: RunningDevicePanel, toolWindow: ToolWindow) {
    if (selectedPanel != panel) {
      val contentManager = toolWindow.contentManager
      val content = contentManager.getContent(panel)
      contentManager.setSelectedContent(content)
    }
  }

  private fun createContent(toolWindow: ToolWindow) {
    if (contentCreated) {
      return
    }
    contentCreated = true

    val actionGroup = DefaultActionGroup()
    actionGroup.addAction(ToggleZoomToolbarAction())
    actionGroup.addAction(ToggleDeviceFrameAction())
    (toolWindow as ToolWindowEx).setAdditionalGearActions(actionGroup)

    val emulatorCatalog = RunningEmulatorCatalog.getInstance()
    emulatorCatalog.updateNow()
    emulatorCatalog.addListener(this, EMULATOR_DISCOVERY_INTERVAL_MILLIS)
    // Ignore standalone emulators.
    emulators.addAll(emulatorCatalog.emulators.filter { it.emulatorId.isEmbedded })

    // Create the panel for the last selected device before other panels so that it becomes selected.
    when (val activeDeviceId = lastSelectedDeviceId) {
      is DeviceId.EmulatorDeviceId -> {
        val activeEmulator = emulators.find { it.emulatorId == activeDeviceId.emulatorId }
        if (activeEmulator != null && !activeEmulator.isShuttingDown) {
          addEmulatorPanel(activeEmulator)
        }
      }
      is DeviceId.PhysicalDeviceId -> {
        val deviceProperties = mirrorableDeviceProperties[activeDeviceId.serialNumber]
        if (deviceProperties != null) {
          physicalDeviceWatcher?.deviceConnected(activeDeviceId.serialNumber, deviceProperties)
        }
      }
      else -> {}
    }

    for (emulator in emulators) {
      if (emulator.emulatorId.serialNumber != lastSelectedDeviceId?.serialNumber && !emulator.isShuttingDown) {
        addEmulatorPanel(emulator)
      }
    }

    for ((serialNumber, deviceProperties) in mirrorableDeviceProperties) {
      if (serialNumber != lastSelectedDeviceId?.serialNumber) {
        physicalDeviceWatcher?.deviceConnected(serialNumber, deviceProperties)
      }
    }

    // Not maintained when the tool window is visible.
    lastSelectedDeviceId = null

    val contentManager = toolWindow.contentManager
    if (contentManager.contentCount == 0) {
      createEmptyStatePanel()
    }

    contentManager.addContentManagerListener(contentManagerListener)
    viewSelectionChanged(toolWindow)
  }

  private fun destroyContent(toolWindow: ToolWindow) {
    if (!contentCreated) {
      return
    }
    contentCreated = false

    lastSelectedDeviceId = selectedPanel?.id

    RunningEmulatorCatalog.getInstance().removeListener(this)
    for (emulator in emulators) {
      emulator.removeConnectionStateListener(connectionStateListener)
    }
    emulators.clear()
    mirroredDevices.clear()
    selectedPanel?.let {
      savedUiState[it.id] = it.destroyContent()
    }
    selectedPanel = null
    panels.clear()
    recentAttentionRequests.invalidateAll()
    recentEmulatorLaunches.invalidateAll()
    val contentManager = toolWindow.contentManager
    contentManager.removeContentManagerListener(contentManagerListener)
    contentManager.removeAllContents(true)
  }

  private fun addEmulatorPanel(emulator: EmulatorController) {
    emulator.addConnectionStateListener(connectionStateListener)
    addPanel(EmulatorToolWindowPanel(project, emulator))
  }

  private fun addPhysicalDevicePanel(serialNumber: String, abi: String, title: String, deviceProperties: Map<String, String>) {
    addPanel(DeviceToolWindowPanel(project, serialNumber, abi, title, deviceProperties))
  }

  private fun addPanel(panel: RunningDevicePanel) {
    val toolWindow = getToolWindow()
    val contentManager = toolWindow.contentManager
    var placeholderContent: Content? = null
    if (panels.isEmpty()) {
      showLiveIndicator(toolWindow)
      if (!contentManager.isEmpty) {
        // Remember the placeholder panel content to remove it later. Deleting it now would leave
        // the tool window empty and cause the contentRemoved method in ToolWindowContentUi to
        // hide it.
        placeholderContent = contentManager.getContent(0)
      }
    }

    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(panel, panel.title, false).apply {
      putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
      isCloseable = panel.isClosable
      tabName = panel.title
      icon = panel.icon
      popupIcon = panel.icon
      setPreferredFocusedComponent(panel::preferredFocusableComponent)
      putUserData(ID_KEY, panel.id)
    }

    panel.zoomToolbarVisible = zoomToolbarIsVisible

    val index = panels.binarySearch(panel, PANEL_COMPARATOR).inv()
    assert(index >= 0)

    if (index >= 0) {
      panels.add(index, panel)
      contentManager.addContent(content, index)

      if (selectedPanel != panel) {
        // Activate the newly added panel if it corresponds to a recently launched or used Emulator.
        val deviceId = panel.id
        if (deviceId is DeviceId.EmulatorDeviceId) {
          val avdId = deviceId.emulatorId.avdId
          if (recentEmulatorLaunches.getIfPresent(avdId) != null) {
            recentEmulatorLaunches.invalidate(avdId)
            contentManager.setSelectedContent(content)
          }
        }
      }

      placeholderContent?.let { contentManager.removeContent(it, true) } // Remove the placeholder panel if it was present.
    }
  }

  private fun removeEmulatorPanel(emulator: EmulatorController) {
    emulator.removeConnectionStateListener(connectionStateListener)

    val panel = findPanelByEmulatorId(emulator.emulatorId) ?: return
    removePanel(panel)
  }

  private fun removePhysicalDevicePanel(serialNumber: String) {
    val panel = findPanelBySerialNumber(serialNumber) ?: return
    removePanel(panel)
  }

  private fun removeAllPhysicalDevicePanels() {
    panels.filterIsInstance<DeviceToolWindowPanel>().forEach(::removePanel)
  }

  private fun removePanel(panel: RunningDevicePanel) {
    val toolWindow = getToolWindow()
    val contentManager = toolWindow.contentManager
    val content = contentManager.getContent(panel)
    contentManager.removeContent(content, true)
  }

  private fun createEmptyStatePanel() {
    val panel = EmptyStatePanel(project)
    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(panel, null, false).apply {
      isCloseable = false
    }
    val contentManager = getContentManager()
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)
  }

  private fun viewSelectionChanged(toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager
    val content = contentManager.selectedContent
    val id = content?.getUserData(ID_KEY)
    if (id != selectedPanel?.id) {
      selectedPanel?.let { panel ->
        savedUiState[panel.id] = panel.destroyContent()
        selectedPanel = null
      }

      if (id != null) {
        selectedPanel = findPanelByDeviceId(id)
        selectedPanel?.createContent(deviceFrameVisible, savedUiState.remove(id))
        ToggleToolbarAction.setToolbarVisible(toolWindow, PropertiesComponent.getInstance(project), null)
      }
    }
  }

  private fun findPanelByDeviceId(deviceId: DeviceId): RunningDevicePanel? {
    return panels.firstOrNull { it.id == deviceId }
  }

  private fun findPanelByEmulatorId(emulatorId: EmulatorId): RunningDevicePanel? {
    return panels.firstOrNull { it.id is DeviceId.EmulatorDeviceId && it.id.emulatorId == emulatorId }
  }

  private fun findPanelByAvdId(avdId: String): RunningDevicePanel? {
    return panels.firstOrNull { it.id is DeviceId.EmulatorDeviceId && it.id.emulatorId.avdId == avdId }
  }

  private fun findPanelBySerialNumber(serialNumber: String): RunningDevicePanel? {
    return panels.firstOrNull { it.id.serialNumber == serialNumber }
  }

  private fun getContentManager(): ContentManager {
    return getToolWindow().contentManager
  }

  private fun getToolWindow(): ToolWindow {
    return ToolWindowManager.getInstance(project).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?:
           throw IllegalStateException("Could not find the $RUNNING_DEVICES_TOOL_WINDOW_TITLE tool window")
  }

  private fun showLiveIndicator(toolWindow: ToolWindow) {
    toolWindow.setIcon(ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.EMULATOR))
  }

  private fun hideLiveIndicator(toolWindow: ToolWindow) {
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.EMULATOR)
  }

  @AnyThread
  override fun emulatorAdded(emulator: EmulatorController) {
    if (emulator.emulatorId.isEmbedded) {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (contentCreated && emulators.add(emulator)) {
          addEmulatorPanel(emulator)
        }
      }
    }
  }

  @AnyThread
  override fun emulatorRemoved(emulator: EmulatorController) {
    if (emulator.emulatorId.isEmbedded) {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (contentCreated && emulators.remove(emulator)) {
          removeEmulatorPanel(emulator)
        }
      }
    }
  }

  override fun settingsChanged(settings: DeviceMirroringSettings) {
    if (settings.deviceMirroringEnabled) {
      if (physicalDeviceWatcher == null) {
        createPhysicalDeviceWatcherIfToolWindowAvailable(ToolWindowManager.getInstance(project))
      }
    }
    else {
      physicalDeviceWatcher?.dispose()
      physicalDeviceWatcher = null
      removeAllPhysicalDevicePanels()
    }
  }

  private fun ToolWindow.showAndActivate() {
    if (isVisible) {
      activate(null)
    }
    else {
      show {
        activate(null)
      }
    }
  }

  private inner class ToggleDeviceFrameAction : ToggleAction("Show Device Frame"), DumbAware {

    override fun update(event: AnActionEvent) {
      super.update(event)
      val panel = selectedPanel
      event.presentation.isEnabledAndVisible = panel is EmulatorToolWindowPanel && panel.emulator.emulatorConfig.skinFolder != null
    }

    override fun isSelected(event: AnActionEvent): Boolean {
      return deviceFrameVisible
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      deviceFrameVisible = state
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private inner class ToggleZoomToolbarAction : ToggleAction("Show Zoom Controls"), DumbAware {

    override fun isSelected(event: AnActionEvent): Boolean {
      return zoomToolbarIsVisible
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      zoomToolbarIsVisible = state
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  companion object {
    private const val DEVICE_FRAME_VISIBLE_PROPERTY = "com.android.tools.idea.emulator.frame.visible"
    private const val DEVICE_FRAME_VISIBLE_DEFAULT = true
    private const val ZOOM_TOOLBAR_VISIBLE_PROPERTY = "com.android.tools.idea.emulator.zoom.toolbar.visible"
    private const val ZOOM_TOOLBAR_VISIBLE_DEFAULT = true
    private const val EMULATOR_DISCOVERY_INTERVAL_MILLIS = 1000

    @JvmStatic
    private val ID_KEY = Key.create<DeviceId>("device-id")

    @JvmStatic
    private val ATTENTION_REQUEST_EXPIRATION = Duration.ofSeconds(30)

    @JvmStatic
    private val COLLATOR = Collator.getInstance()

    @JvmStatic
    private val PANEL_COMPARATOR = compareBy<RunningDevicePanel, Any?>(COLLATOR) { it.title }.thenBy { it.id }

    @JvmStatic
    private val registeredProjects: MutableSet<Project> = hashSetOf()

    /**
     * Initializes [EmulatorToolWindowManager] for the given project. Repeated calls for the same project
     * are ignored.
     */
    @AnyThread
    @JvmStatic
    fun initializeForProject(project: Project) {
      if (registeredProjects.add(project)) {
        Disposer.register(project.earlyDisposable) { registeredProjects.remove(project) }
        EmulatorToolWindowManager(project)
      }
    }

    @AnyThread
    @JvmStatic
    private fun isEmbeddedEmulator(commandLine: GeneralCommandLine) =
      commandLine.parametersList.parameters.contains("-qt-hide-window")
  }

  private inner class PhysicalDeviceWatcher(disposableParent: Disposable) : Disposable {
    private val adbSession = AdbLibService.getSession(project)
    private val coroutineScope: CoroutineScope
    private var onlineDevices = setOf<String>()

    init {
      Disposer.register(disposableParent, this)
      val executor = createBoundedApplicationPoolExecutor("EmulatorToolWindowManager.PhysicalDeviceWatcher", 1)
      coroutineScope = AndroidCoroutineScope(this, executor.asCoroutineDispatcher())
      coroutineScope.launch {
        adbSession.trackDevices().collect { deviceList ->
          UIUtil.invokeLaterIfNeeded {
            onlineDevices = deviceList.devices.entries.filter { it.deviceState == DeviceState.ONLINE }.map(DeviceInfo::serialNumber).toSet()
            onlineDevicesChanged()
          }
        }
      }
    }

    private fun onlineDevicesChanged() {
      val removed = mirrorableDeviceProperties.keys.minus(onlineDevices)
      mirroredDevices.removeAll(removed)
      mirrorableDeviceProperties.keys.removeAll(removed)
      for (device in removed) {
        removePhysicalDevicePanel(device)
      }
      for (deviceSerialNumber in onlineDevices) {
        if (!mirroredDevices.contains(deviceSerialNumber)) {
          coroutineScope.launch {
            deviceConnected(deviceSerialNumber)
          }
        }
      }
    }

    @AnyThread
    private suspend fun deviceConnected(deviceSerialNumber: String) {
      val deviceProperties = getMirrorableDeviceProperties(deviceSerialNumber)
      if (deviceProperties != null) {
        UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
          if (deviceSerialNumber in onlineDevices && mirrorableDeviceProperties.put(deviceSerialNumber, deviceProperties) == null) {
            if (contentCreated) {
              deviceConnected(deviceSerialNumber, deviceProperties)
            }
            else if (recentAttentionRequests.getIfPresent(deviceSerialNumber) != null) {
              recentAttentionRequests.invalidate(deviceSerialNumber)
              lastSelectedDeviceId = DeviceId.ofPhysicalDevice(deviceSerialNumber)
              getToolWindow().showAndActivate()
            }
          }
        }
      }
    }

    fun deviceConnected(deviceSerialNumber: String, deviceProperties: Map<String, String>) {
      if (!mirroredDevices.contains(deviceSerialNumber)) {
        val deviceName = getDeviceName(deviceProperties, deviceSerialNumber)
        val deviceAbi = deviceProperties[RO_PRODUCT_CPU_ABI]
        if (deviceAbi == null) {
          thisLogger().warn("Unable to determine ABI of $deviceName")
          return
        }

        if (deviceMirroringSettings.confirmationDialogShown) {
          startMirroring(deviceSerialNumber, deviceAbi, deviceName, deviceProperties)
        }
        else {
          val dialog = MirroringConfirmationDialog(deviceName)
          val dialogWrapper = dialog.createWrapper(project).apply { show() }
          when (dialogWrapper.exitCode) {
            MirroringConfirmationDialog.ACCEPT_EXIT_CODE -> startMirroring(deviceSerialNumber, deviceAbi, deviceName, deviceProperties)
            MirroringConfirmationDialog.REJECT_EXIT_CODE -> deviceMirroringSettings.deviceMirroringEnabled = false
            else -> return
          }
          deviceMirroringSettings.confirmationDialogShown = true
        }
      }
    }

    private fun startMirroring(deviceSerialNumber: String, deviceAbi: String, deviceName: String, deviceProperties: Map<String, String>) {
      if (deviceSerialNumber in onlineDevices && mirroredDevices.add(deviceSerialNumber)) {
        if (contentCreated) {
          addPhysicalDevicePanel(deviceSerialNumber, deviceAbi, deviceName, deviceProperties)
        }
      }
    }

    /** Returns properties of the device if it supports mirroring. Otherwise, returns null. */
    @AnyThread
    private suspend fun getMirrorableDeviceProperties(deviceSerialNumber: String): Map<String, String>? {
      if (deviceSerialNumber.startsWith("emulator-")) {
        if (!StudioFlags.DEVICE_MIRRORING_STANDALONE_EMULATORS.get()) {
          return null
        }
        val emulators = RunningEmulatorCatalog.getInstance().updateNow().suspendingGet()
        val emulator = emulators.find { "emulator-${it.emulatorId.serialPort}" == deviceSerialNumber }
        if (emulator == null || emulator.emulatorId.isEmbedded) {
          return null
        }
      }
      try {
        val properties = adbSession.deviceServices.deviceProperties(DeviceSelector.fromSerialNumber(deviceSerialNumber)).allReadonly()
        val apiLevel = properties[DevicePropertyNames.RO_BUILD_VERSION_SDK]?.toInt() ?: SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
        if (apiLevel < 26) {
          return null // Mirroring is supported for API >= 26.
        }
        val isWatch = properties[RO_BUILD_CHARACTERISTICS]?.contains("watch") ?: false
        if (isWatch && apiLevel < 30) {
          return null // Wear OS devices with API < 30 don't support VP8/VP9 video encoders.
        }
        return properties
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        thisLogger().warn(e)
        return null
      }
    }

    private fun getDeviceName(deviceProperties: Map<String, String>, deviceSerialNumber: String): String {
      var name = (deviceProperties[RO_BOOT_QEMU_AVD_NAME] ?: deviceProperties[RO_KERNEL_QEMU_AVD_NAME])?.replace('_', ' ')
      if (name == null) {
        name = deviceProperties[RO_PRODUCT_MODEL] ?: deviceSerialNumber
        val manufacturer = deviceProperties[RO_PRODUCT_MANUFACTURER]
        if (!manufacturer.isNullOrBlank() && manufacturer != "unknown") {
          name = "${WordUtils.capitalize(manufacturer)} $name"
        }
      }
      return name
    }

    override fun dispose() {
    }
  }
}
