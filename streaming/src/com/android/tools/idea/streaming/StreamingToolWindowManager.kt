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
package com.android.tools.idea.streaming

import com.android.adblib.serialNumber
import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.mapStateNotNull
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiAction
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.streaming.RunningDevicePanel.UiState
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.DeviceConfiguration
import com.android.tools.idea.streaming.device.DeviceToolWindowPanel
import com.android.tools.idea.streaming.device.dialogs.MirroringConfirmationDialog
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.streaming.emulator.EmulatorId
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.google.common.cache.CacheBuilder
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ToggleToolbarAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
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
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.Alarm
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.AppExecutorUtil.createBoundedApplicationPoolExecutor
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.awt.event.KeyEvent
import java.text.Collator
import java.time.Duration

private const val DEVICE_FRAME_VISIBLE_PROPERTY = "com.android.tools.idea.streaming.emulator.frame.visible"
private const val DEVICE_FRAME_VISIBLE_DEFAULT = true
private const val ZOOM_TOOLBAR_VISIBLE_PROPERTY = "com.android.tools.idea.streaming.emulator.zoom.toolbar.visible"
private const val ZOOM_TOOLBAR_VISIBLE_DEFAULT = true
private const val EMULATOR_DISCOVERY_INTERVAL_MILLIS = 1000

private val ID_KEY = Key.create<DeviceId>("device-id")

private val ATTENTION_REQUEST_EXPIRATION = Duration.ofSeconds(30)

private val COLLATOR = Collator.getInstance()

private val PANEL_COMPARATOR = compareBy<RunningDevicePanel, Any?>(COLLATOR) { it.title }.thenBy { it.id }

/**
 * Manages contents of the Running Devices tool window. Listens to device connections and
 * disconnections and maintains [RunningDevicePanel]s, one per running AVD or a mirrored physical
 * device.
 */
@UiThread
internal class StreamingToolWindowManager @AnyThread constructor(
  private val toolWindow: ToolWindow
) : RunningEmulatorCatalog.Listener, DeviceMirroringSettingsListener, DumbAware, Disposable {

  private val project
    @AnyThread get() = toolWindow.project
  private val emulatorSettings = EmulatorSettings.getInstance()
  private val deviceMirroringSettings = DeviceMirroringSettings.getInstance()
  private var initialized = false
  private var contentCreated = false
  private var mirroringConfirmationDialogShowing = false
  private var physicalDeviceWatcher: PhysicalDeviceWatcher? = null
  private val panels = arrayListOf<RunningDevicePanel>()
  private var selectedPanel: RunningDevicePanel? = null

  /** When the tool window is hidden, the ID of the last selected device, otherwise null. */
  private var lastSelectedDeviceId: DeviceId? = null

  /** When the tool window is hidden, the state of the UI for all emulators, otherwise empty. */
  private val savedUiState = hashMapOf<DeviceId, UiState>()
  private val emulators = hashSetOf<EmulatorController>()

  private var onlineDevices = mapOf<String, ConnectedDevice>()
  /** Clients for mirrorable devices keyed by serial numbers. */
  private var deviceClients = mutableMapOf<String, DeviceClient>()

  /** Serial numbers of mirrored devices. */
  private var mirroredDevices = mutableSetOf<String>()
  /** Handles of devices excluded from mirroring keyed by serial numbers. */
  private var devicesExcludedFromMirroring = mutableMapOf<String, DeviceDescription>()
  private val properties = PropertiesComponent.getInstance(project)

  // Serial numbers of devices that recently requested attention.
  private val recentAttentionRequests = CacheBuilder.newBuilder().expireAfterWrite(ATTENTION_REQUEST_EXPIRATION).build<String, String>()

  // IDs of recently launched AVDs keyed by themselves.
  private val recentEmulatorLaunches = CacheBuilder.newBuilder().expireAfterWrite(ATTENTION_REQUEST_EXPIRATION).build<String, String>()
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

  private val contentManagerListener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      viewSelectionChanged()
    }

    override fun contentRemoveQuery(event: ContentManagerEvent) {
      val panel = event.content.component as? RunningDevicePanel ?: return
      when (panel) {
        is EmulatorToolWindowPanel -> panel.emulator.shutdown()
        is DeviceToolWindowPanel -> stopMirroring(panel.deviceSerialNumber)
      }

      panels.remove(panel)
      savedUiState.remove(panel.id)
      if (panels.isEmpty()) {
        if (contentCreated) {
          createEmptyStatePanel()
        }
        hideLiveIndicator()
      }
    }
  }

  private fun stopMirroring(serialNumber: String) {
    mirroredDevices.remove(serialNumber)
    val deviceClient = deviceClients.remove(serialNumber)
    if (deviceClient != null) {
      devicesExcludedFromMirroring[serialNumber] =
          DeviceDescription(deviceClient.deviceName, serialNumber, deviceClient.deviceHandle, deviceClient.deviceConfig)
      Disposer.dispose(deviceClient)
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
    Disposer.register(toolWindow.disposable, this)

    if (StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.get()) {
      val newTabAction = NewTabAction()
      newTabAction.registerCustomShortcutSet(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK, toolWindow.component)
      (toolWindow as ToolWindowEx).setTabActions(newTabAction)
    }

    // Lazily initialize content since we can only have one frame.
    val messageBusConnection = project.messageBus.connect(this)
    messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {

      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return

        toolWindowManager.invokeLater {
          if (!toolWindow.isDisposed) {
            if (toolWindow.isVisible) {
              createContent()
            }
            else {
              destroyContent()
            }
          }
        }
      }
    })

    messageBusConnection.subscribe(AvdLaunchListener.TOPIC,
                                   AvdLaunchListener { avd, commandLine, requestType, project ->
                                     if (project == toolWindow.project && isEmbeddedEmulator(commandLine)) {
                                       RunningEmulatorCatalog.getInstance().updateNow()
                                       EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
                                         showLiveIndicator()
                                         if (requestType == AvdLaunchListener.RequestType.DIRECT) {
                                           onEmulatorHeadsUp(avd.name)
                                         }
                                       }
                                     }
                                   })

    messageBusConnection.subscribe(DeviceHeadsUpListener.TOPIC, MyDeviceHeadsUpListener())

    messageBusConnection.subscribe(DeviceMirroringSettingsListener.TOPIC, this)

    if (deviceMirroringSettings.deviceMirroringEnabled) {
      UIUtil.invokeLaterIfNeeded {
        if (!toolWindow.isDisposed) {
          physicalDeviceWatcher = PhysicalDeviceWatcher(this)
        }
      }
    }
  }

  private fun onDeviceHeadsUp(deviceSerialNumber: String) {
    if (deviceClients.contains(deviceSerialNumber)) {
      onPhysicalDeviceHeadsUp(deviceSerialNumber)
    }
    else {
      recentAttentionRequests.put(deviceSerialNumber, deviceSerialNumber)
      alarm.addRequest(recentAttentionRequests::cleanUp, ATTENTION_REQUEST_EXPIRATION.toMillis())
      if (isLocalEmulator(deviceSerialNumber)) {
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
    if (toolWindow.isVisible) {
      val panel = findPanelBySerialNumber(deviceSerialNumber)
      if (panel != null) {
        selectPanel(panel)
        toolWindow.showAndActivate()
      }
    }
    else {
      lastSelectedDeviceId = DeviceId.ofPhysicalDevice(deviceSerialNumber)
      toolWindow.showAndActivate()
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
    toolWindow.showAndActivate()

    val panel = findPanelByAvdId(avdId)
    if (panel == null) {
      RunningEmulatorCatalog.getInstance().updateNow()
      recentEmulatorLaunches.put(avdId, avdId)
      alarm.addRequest(recentEmulatorLaunches::cleanUp, ATTENTION_REQUEST_EXPIRATION.toMillis())
    }
    else {
      selectPanel(panel)
    }
  }

  private fun selectPanel(panel: RunningDevicePanel) {
    if (selectedPanel != panel) {
      val contentManager = toolWindow.contentManager
      val content = contentManager.getContent(panel)
      contentManager.setSelectedContent(content)
    }
  }

  private fun createContent() {
    if (!initialized) {
      initialized = true
      toolWindow.contentManager.addDataProvider { dataId -> getDataFromSelectedPanel(dataId) }
      val actionGroup = DefaultActionGroup()
      actionGroup.addAction(ToggleZoomToolbarAction())
      actionGroup.addAction(ToggleDeviceFrameAction())
      toolWindow.setAdditionalGearActions(actionGroup)
    }

    if (contentCreated) {
      return
    }
    contentCreated = true

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
        val deviceClient = deviceClients[activeDeviceId.serialNumber]
        if (deviceClient != null) {
          activateMirroring(activeDeviceId.serialNumber, deviceClient)
        }
      }

      else -> {}
    }

    for (emulator in emulators) {
      if (emulator.emulatorId.serialNumber != lastSelectedDeviceId?.serialNumber && !emulator.isShuttingDown) {
        addEmulatorPanel(emulator)
      }
    }

    for ((serialNumber, deviceClient) in deviceClients) {
      if (serialNumber != lastSelectedDeviceId?.serialNumber) {
        activateMirroring(serialNumber, deviceClient)
      }
    }

    // Not maintained when the tool window is visible.
    lastSelectedDeviceId = null

    val contentManager = toolWindow.contentManager
    if (contentManager.contentCount == 0) {
      createEmptyStatePanel()
    }

    contentManager.addContentManagerListener(contentManagerListener)
    viewSelectionChanged()
  }

  private fun destroyContent() {
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

  private fun addPhysicalDevicePanel(deviceClient: DeviceClient) {
    addPanel(DeviceToolWindowPanel(project, deviceClient))
  }

  private fun addPanel(panel: RunningDevicePanel) {
    val contentManager = toolWindow.contentManager
    var placeholderContent: Content? = null
    if (panels.isEmpty()) {
      showLiveIndicator()
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
    val panel = findPanelBySerialNumber(serialNumber) as? DeviceToolWindowPanel ?: return
    removePhysicalDevicePanel(panel)
  }

  private fun removePhysicalDevicePanel(panel: DeviceToolWindowPanel) {
    val serialNumber = panel.id.serialNumber
    deviceClients.remove(serialNumber)?.let { Disposer.dispose(it) }
    mirroredDevices.remove(serialNumber)
    removePanel(panel)
  }

  private fun removeAllPhysicalDevicePanels() {
    panels.filterIsInstance<DeviceToolWindowPanel>().forEach(::removePhysicalDevicePanel)
  }

  private fun removePanel(panel: RunningDevicePanel) {
    val contentManager = toolWindow.contentManager
    val content = contentManager.getContent(panel)
    if (content != null) {
      contentManager.removeContent(content, true)
    }
  }

  private fun createEmptyStatePanel() {
    val panel = try {
      EmptyStatePanel(project, this)
    }
    catch (e: IncorrectOperationException) {
      // This object has been disposed already.
      return
    }
    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(panel, null, false).apply {
      isCloseable = false
    }
    val contentManager = getContentManager()
    try {
      contentManager.addContent(content)
      contentManager.setSelectedContent(content)
    }
    catch (e: IncorrectOperationException) {
      // Content manager has been disposed already.
      Disposer.dispose(content)
    }
  }

  private fun viewSelectionChanged() {
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

  private fun getDataFromSelectedPanel(dataId: String): Any? {
    val selectedContent = toolWindow.contentManager.selectedContent ?: return null
    val panelId = selectedContent.getUserData(ID_KEY) ?: return null
    val panel = findPanelByDeviceId(panelId) ?: return null
    return panel.getData(dataId)
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
    return toolWindow.contentManager
  }

  private fun showLiveIndicator() {
    toolWindow.setIcon(ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.EMULATOR))
  }

  private fun hideLiveIndicator() {
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
        physicalDeviceWatcher = PhysicalDeviceWatcher(this)
      }
    }
    else {
      physicalDeviceWatcher?.let { Disposer.dispose(it) }
      physicalDeviceWatcher = null
    }
  }

  override fun dispose() {
    destroyContent()
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

  private fun activateMirroring(deviceDescription: DeviceDescription) {
    val serialNumber = deviceDescription.serialNumber
    val deviceClient =
        physicalDeviceWatcher?.getOrCreateDeviceClient(serialNumber, deviceDescription.handle, deviceDescription.config) ?: return
    if (!mirroredDevices.contains(serialNumber)) {
      startMirroringIfConfirmed(serialNumber, deviceClient)
    }
    onPhysicalDeviceHeadsUp(serialNumber)
  }

  private fun activateMirroring(serialNumber: String, deviceClient: DeviceClient) {
    if (!mirroredDevices.contains(serialNumber) && !devicesExcludedFromMirroring.contains(serialNumber)) {
      startMirroringIfConfirmed(serialNumber, deviceClient)
    }
  }

  private fun startMirroringIfConfirmed(serialNumber: String, deviceClient: DeviceClient) {
    if (deviceMirroringSettings.confirmationDialogShown) {
      startMirroring(serialNumber, deviceClient)
    }
    else if (!mirroringConfirmationDialogShowing) { // Ignore a recursive call inside the dialog's event loop.
      mirroringConfirmationDialogShowing = true
      val title = "About to Start Mirroring of ${deviceClient.deviceName}"
      val dialogWrapper = MirroringConfirmationDialog(title).createWrapper(project).apply { show() }
      mirroringConfirmationDialogShowing = false
      when (dialogWrapper.exitCode) {
        MirroringConfirmationDialog.ACCEPT_EXIT_CODE -> startMirroring(serialNumber, deviceClient)
        MirroringConfirmationDialog.REJECT_EXIT_CODE -> deviceMirroringSettings.deviceMirroringEnabled = false
        else -> return
      }
      deviceMirroringSettings.confirmationDialogShown = true
    }
  }

  private fun startMirroring(serialNumber: String, deviceClient: DeviceClient) {
    devicesExcludedFromMirroring.remove(serialNumber)
    if (serialNumber in onlineDevices && mirroredDevices.add(serialNumber)) {
      deviceClient.establishAgentConnectionWithoutVideoStreamAsync() // Start the agent and connect to it proactively.
      showLiveIndicator()
      if (contentCreated) {
        addPhysicalDevicePanel(deviceClient)
      }
    }
  }

  private fun createMirroringActions(): DefaultActionGroup {
    return DefaultActionGroup().apply {
      val deviceDescriptions = devicesExcludedFromMirroring.values.toTypedArray().sortedBy { it.deviceName }
      if (deviceDescriptions.isNotEmpty()) {
        add(Separator("Connected Physical Devices"))
        for (deviceDescription in deviceDescriptions) {
          add(StartMirroringAction(deviceDescription))
        }
        add(Separator.getInstance())
      }
      add(ActionManager.getInstance().getAction(PairDevicesUsingWiFiAction.ID))
    }
  }

  private inner class MyDeviceHeadsUpListener : DeviceHeadsUpListener {

    override fun userInvolvementRequired(deviceSerialNumber: String, project: Project) {
      if (project == toolWindow.project) {
        UIUtil.invokeLaterIfNeeded {
          onDeviceHeadsUp(deviceSerialNumber)
        }
      }
    }

    override fun launchingApp(deviceSerialNumber: String, project: Project) {
      val activate =
          if (isLocalEmulator(deviceSerialNumber)) emulatorSettings.activateOnAppLaunch else deviceMirroringSettings.activateOnAppLaunch
      if (activate) {
        userInvolvementRequired(deviceSerialNumber, project)
      }
    }

    override fun launchingTest(deviceSerialNumber: String, project: Project) {
      val activate =
        if (isLocalEmulator(deviceSerialNumber)) emulatorSettings.activateOnTestLaunch else deviceMirroringSettings.activateOnTestLaunch
      if (activate) {
        userInvolvementRequired(deviceSerialNumber, project)
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

  private inner class PhysicalDeviceWatcher(disposableParent: Disposable) : Disposable {
    private val coroutineScope: CoroutineScope
    private var deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner

    init {
      Disposer.register(disposableParent, this)
      val executor = createBoundedApplicationPoolExecutor("EmulatorToolWindowManager.PhysicalDeviceWatcher", 1)
      coroutineScope = AndroidCoroutineScope(this, executor.asCoroutineDispatcher())
      coroutineScope.launch {
        deviceProvisioner.mirrorableDevicesBySerialNumber().collect { newOnlineDevices ->
          UIUtil.invokeLaterIfNeeded {
            onlineDevices = newOnlineDevices
            onlineDevicesChanged()
          }
        }
      }
    }

    private fun onlineDevicesChanged() {
      val removed = deviceClients.keys.minus(onlineDevices.keys)
      for (device in removed) {
        removePhysicalDevicePanel(device)
      }
      if (!toolWindow.isVisible && deviceClients.isEmpty() && emulators.isEmpty() && removed.isNotEmpty()) {
        hideLiveIndicator()
      }
      for ((serialNumber, device) in onlineDevices) {
        if (!mirroredDevices.contains(serialNumber)) {
          coroutineScope.launch {
            deviceConnected(serialNumber, device)
          }
        }
      }
    }

    @AnyThread
    private fun deviceConnected(serialNumber: String, device: ConnectedDevice) {
      val config = DeviceConfiguration(device.state.properties, useTitleAsName = isLocalEmulator(serialNumber))
      UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
        deviceConnected(serialNumber, device, config)
      }
    }

    private fun deviceConnected(serialNumber: String, device: ConnectedDevice, config: DeviceConfiguration) {
      if (serialNumber in onlineDevices) {
        val deviceClient = getOrCreateDeviceClient(serialNumber, device.handle, config)
        if (contentCreated) {
          activateMirroring(serialNumber, deviceClient)
          if (recentAttentionRequests.getIfPresent(serialNumber) != null) {
            recentAttentionRequests.invalidate(serialNumber)
            onPhysicalDeviceHeadsUp(serialNumber)
          }
        }
        else if (deviceMirroringSettings.activateOnConnection || recentAttentionRequests.getIfPresent(serialNumber) != null) {
          recentAttentionRequests.invalidate(serialNumber)
          lastSelectedDeviceId = DeviceId.ofPhysicalDevice(serialNumber)
          toolWindow.showAndActivate()
        }
      }
    }

    fun getOrCreateDeviceClient(serialNumber: String, deviceHandle: DeviceHandle, config: DeviceConfiguration): DeviceClient {
      return deviceClients.computeIfAbsent(serialNumber) { serial ->
        DeviceClient(this, serial, deviceHandle, config, config.deviceProperties.abi.toString(), project)
      }
    }

    override fun dispose() {
      deviceClients.clear() // The clients have been disposed already.
      removeAllPhysicalDevicePanels()
    }
  }

  private inner class NewTabAction : DumbAwareAction("New Tab", "Show a new device", AllIcons.General.Add), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
      val actionGroup = createMirroringActions()

      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
          null, actionGroup, event.dataContext,
          if (actionGroup.childrenCount > 1) ActionSelectionAid.NUMBERING else ActionSelectionAid.SPEEDSEARCH,
          true, null, -1, null,
          ActionPlaces.getActionGroupPopupPlace(ActionPlaces.TOOLWINDOW_TOOLBAR_BAR))

      val component = event.inputEvent?.component
      val actionComponent = if (component is ActionButtonComponent) component else event.findComponentForAction(this)
      if (actionComponent == null) {
        popup.showInFocusCenter()
      }
      else {
        popup.showUnderneathOf(actionComponent)
      }
      // Clear initial selection.
      (popup as? ListPopupImpl)?.list?.clearSelection()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private inner class StartMirroringAction(private val device: DeviceDescription) : DumbAwareAction(device.deviceName) {

    override fun actionPerformed(event: AnActionEvent) {
      activateMirroring(device)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private class DeviceDescription(val deviceName: String, val serialNumber: String, val handle: DeviceHandle,
                                  val config: DeviceConfiguration)
}

private class ConnectedDevice(val handle: DeviceHandle, val state: DeviceState.Connected)

private fun DeviceProvisioner.mirrorableDevicesBySerialNumber(): Flow<Map<String, ConnectedDevice>> {
  return connectedDevices().map { connectedDevices ->
    connectedDevices.filter { it.state.isMirrorable() }.associateBy { it.state.serialNumber }
  }
}

private fun DeviceProvisioner.connectedDevices(): Flow<List<ConnectedDevice>> {
  return mapStateNotNull { handle, state -> (state as? DeviceState.Connected)?.let { ConnectedDevice(handle, it) } }
}

private suspend fun DeviceState.Connected.isMirrorable(): Boolean {
  if (!isOnline()) {
    return false
  }

  val deviceSerialNumber = serialNumber
  if (isLocalEmulator(deviceSerialNumber) || properties.isVirtual == true) {
    if (!StudioFlags.DEVICE_MIRRORING_STANDALONE_EMULATORS.get()) {
      return false
    }
    if (isLocalEmulator(deviceSerialNumber)) {
      val emulators = RunningEmulatorCatalog.getInstance().updateNow().suspendingGet()
      val emulator = emulators.find { "emulator-${it.emulatorId.serialPort}" == deviceSerialNumber }
      if (emulator == null || emulator.emulatorId.isEmbedded) {
        return false
      }
    }
  }

  val apiLevel = properties.androidVersion?.apiLevel ?: SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
  // Mirroring is supported for API >= 26. Wear OS devices with API < 30 don't support VP8/VP9 video encoders.
  return apiLevel >= 26 && (properties.deviceType != DeviceType.WEAR || apiLevel >= 30) && properties.abi != null
}

private val DeviceState.Connected.serialNumber: String
    get() = connectedDevice.serialNumber

private fun isLocalEmulator(deviceSerialNumber: String) =
    deviceSerialNumber.startsWith("emulator-")

private fun isEmbeddedEmulator(commandLine: GeneralCommandLine) =
    commandLine.parametersList.parameters.contains("-qt-hide-window")
