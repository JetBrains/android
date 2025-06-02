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
package com.android.tools.idea.streaming.core

import com.android.adblib.serialNumber
import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.ReservationState
import com.android.sdklib.deviceprovisioner.mapStateNotNull
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiAction
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.deviceprovisioner.launchCatchingDeviceActionException
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.MirroringHandle
import com.android.tools.idea.streaming.MirroringManager
import com.android.tools.idea.streaming.MirroringState
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.actions.ToggleFloatingXrToolbarAction
import com.android.tools.idea.streaming.actions.toolWindowContents
import com.android.tools.idea.streaming.core.StreamingDevicePanel.UiState
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.DeviceConfiguration
import com.android.tools.idea.streaming.device.DeviceToolWindowPanel
import com.android.tools.idea.streaming.device.composeDeviceName
import com.android.tools.idea.streaming.device.dialogs.MirroringConfirmationDialog
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.streaming.emulator.EmulatorId
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.streaming.emulator.displayNameWithApi
import com.android.utils.TraceUtils.simpleId
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ToggleToolbarAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.EdtNoGetDataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ex.MessagesEx.showErrorDialog
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.ActivateToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.MovedOrResized
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.ShowToolWindow
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.ComponentUtil
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerEvent.ContentOperation
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentImpl.PROP_CONTENT_MANAGER
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.Alarm
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.AppExecutorUtil.createBoundedApplicationPoolExecutor
import com.intellij.util.containers.ComparatorUtil.max
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.KeyEvent
import java.util.function.Supplier
import javax.swing.JComponent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private const val DEVICE_FRAME_VISIBLE_PROPERTY = "com.android.tools.idea.streaming.emulator.frame.visible"
private const val DEVICE_FRAME_VISIBLE_DEFAULT = true
private const val ZOOM_TOOLBAR_VISIBLE_PROPERTY = "com.android.tools.idea.streaming.zoom.toolbar.visible"
private const val ZOOM_TOOLBAR_VISIBLE_DEFAULT = true
private const val EMULATOR_DISCOVERY_INTERVAL_MILLIS = 1000L

private val CONTENT_DEVICE_ID_KEY = Key.create<DeviceId>("DeviceId")

private val ATTENTION_REQUEST_EXPIRATION = 30.seconds
private val REMOTE_DEVICE_REQUEST_EXPIRATION = 60.seconds
private val AUTO_RECONNECTION_TIMEOUT = 5.seconds // Auto reconnection timeout is just long enough to reconnect after adb kill-server.

@VisibleForTesting
internal val INACTIVE_ICON = StudioIcons.Shell.ToolWindows.EMULATOR
@VisibleForTesting
internal val LIVE_ICON = BadgeIconSupplier(INACTIVE_ICON).liveIndicatorIcon

/**
 * Manages contents of the Running Devices tool window. Listens to device connections and
 * disconnections and maintains [StreamingDevicePanel]s, one per running AVD or a mirrored physical
 * device.
 */
@UiThread
internal class StreamingToolWindowManager @AnyThread constructor(
  private val toolWindow: ToolWindowEx,
) : RunningEmulatorCatalog.Listener, DeviceClientRegistry.Listener, DumbAware, Disposable {

  private val project
    @AnyThread get() = toolWindow.project
  private val projectProperties = PropertiesComponent.getInstance(project)
  private val emulatorSettings = EmulatorSettings.getInstance()
  private val deviceMirroringSettings = DeviceMirroringSettings.getInstance()
  private val deviceProvisioner
    @AnyThread get() = project.service<DeviceProvisionerService>().deviceProvisioner
  private val deviceClientRegistry = service<DeviceClientRegistry>()
  private var initialized = false
  private var contentShown = false
  private var initialContentUpdate = false
  private var mirroringConfirmationDialogShowing = false

  /** When the tool window is hidden, the state of the UI for all emulators, otherwise empty. */
  private val savedUiState = hashMapOf<DeviceId, UiState>()
  private val emulators = hashSetOf<EmulatorController>()

  private var onlineDevices = mapOf<String, ConnectedDevice>()
  /** Clients and handles of mirrorable devices keyed by serial numbers. */
  private var deviceClients = mutableMapOf<String, DeviceClientWithHandle>()
  /** Handles of devices excluded from mirroring keyed by serial numbers. */
  private var devicesExcludedFromMirroring = mutableMapOf<String, DeviceDescription>()

  /** Requested activation levels of devices that recently requested attention keyed by their serial numbers. */
  private val recentAttentionRequests = buildCache<String, ActivationLevel>(ATTENTION_REQUEST_EXPIRATION)
  /** Requested activation levels of AVDs keyed by their IDs. */
  private val recentAvdLaunches = buildCache<String, ActivationLevel>(ATTENTION_REQUEST_EXPIRATION)
  /** Recently disconnected mirrored devices. */
  private val recentDisconnections = buildCache<String, ActivationLevel>(AUTO_RECONNECTION_TIMEOUT)
  /** Links pending AVD starts to the content managers that requested them. Keyed by AVD IDs. */
  private val recentAvdStartRequesters = buildCache<String, ContentManager>(ATTENTION_REQUEST_EXPIRATION)
  /** Links pending remote device mirroring starts to the content managers that requested them. Keyed by AVD IDs. */
  private val recentRemoteDeviceRequesters = buildWeakCache<DeviceHandle, ContentManager>(REMOTE_DEVICE_REQUEST_EXPIRATION)

  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private val toolWindowScope = createCoroutineScope(extraContext = Dispatchers.EDT)

  // Copy-on-write to allow changes while iterating.
  private val contentManagers = ContainerUtil.createLockFreeCopyOnWriteList<ContentManager>()

  private val contentManagerListener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation != ContentOperation.remove || !Content.TEMPORARY_REMOVED_KEY.get(event.content, false)) {
        viewSelectionChanged()
      }
    }

    override fun contentAdded(event: ContentManagerEvent) {
      event.content.addPropertyChangeListener { evt ->
        if (evt.propertyName == PROP_CONTENT_MANAGER) {
          val contentManager = evt.newValue as? ContentManager
          contentManager?.let { adoptContentManager(it) }
        }
      }
    }

    override fun contentRemoveQuery(event: ContentManagerEvent) {
      val content = event.content
      if (Content.TEMPORARY_REMOVED_KEY.get(content, false)) {
        return
      }
      val panel = content.component as? StreamingDevicePanel<*> ?: return
      if (!initialContentUpdate) {
        when (panel) {
          is EmulatorToolWindowPanel -> panel.emulator.shutdown()
          is DeviceToolWindowPanel -> panelClosed(panel)
        }
      }

      savedUiState.remove(panel.id)
      if (contentManagers.size == 1 && content.manager?.contentCount == 1) {
        if (contentShown) {
          createEmptyStatePanel()
        }
        setLiveIndicator(false)
      }
    }
  }

  private val connectionStateListener = object : ConnectionStateListener {
    @AnyThread
    override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
      if (connectionState == ConnectionState.DISCONNECTED) {
        EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
          if (removeEmulatorPanel(emulator)) {
            emulators.remove(emulator)
          }
        }
      }
    }
  }

  private var deviceFrameVisible
    get() = projectProperties.getBoolean(DEVICE_FRAME_VISIBLE_PROPERTY, DEVICE_FRAME_VISIBLE_DEFAULT)
    set(value) {
      projectProperties.setValue(DEVICE_FRAME_VISIBLE_PROPERTY, value, DEVICE_FRAME_VISIBLE_DEFAULT)
      for (contentManager in contentManagers) {
        for (i in 0 until contentManager.contentCount) {
          (contentManager.getContent(i)?.component as? StreamingDevicePanel<*>)?.setDeviceFrameVisible(value)
        }
      }
    }

  private var zoomToolbarIsVisible
    get() = projectProperties.getBoolean(ZOOM_TOOLBAR_VISIBLE_PROPERTY, ZOOM_TOOLBAR_VISIBLE_DEFAULT)
    set(value) {
      projectProperties.setValue(ZOOM_TOOLBAR_VISIBLE_PROPERTY, value, ZOOM_TOOLBAR_VISIBLE_DEFAULT)
      for (contentManager in contentManagers) {
        for (i in 0 until contentManager.contentCount) {
          (contentManager.getContent(i)?.component as? StreamingDevicePanel<*>)?.zoomToolbarVisible = value
        }
      }
    }

  init {
    Disposer.register(toolWindow.disposable, this)
    deviceClientRegistry.addListener(this)
    PhysicalDeviceWatcher(this)

    // Lazily initialize content since we can only have one frame.
    val messageBusConnection = project.messageBus.connect(this)
    messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {

      // TODO: Override the stateChanged method that takes a ToolWindow when it becomes a public API.
      override fun stateChanged(toolWindowManager: ToolWindowManager, changeType: ToolWindowManagerEventType) {
        val toolWindow = toolWindowManager.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return

        when (changeType) {
          ActivateToolWindow, ShowToolWindow, HideToolWindow, MovedOrResized -> {
            toolWindowManager.invokeLater {
              if (!toolWindow.isDisposed) {
                if (toolWindow.isVisible) {
                  initialContentUpdate = true
                  try {
                    onToolWindowShown()
                  }
                  finally {
                    initialContentUpdate = false
                  }
                }
                else {
                  onToolWindowHidden()
                }
              }
            }
          }
          else -> {}
        }
      }
    })

    messageBusConnection.subscribe(AvdLaunchListener.TOPIC,
                                   AvdLaunchListener @AnyThread { avd, commandLine, requestType, project ->
                                     if (project == toolWindow.project && isEmbeddedEmulator(commandLine)) {
                                       RunningEmulatorCatalog.getInstance().updateNow()
                                       EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
                                         if (requestType != RequestType.INDIRECT) {
                                           onEmulatorHeadsUp(avd.name, ActivationLevel.ACTIVATE_TAB)
                                         }
                                       }
                                     }
                                   })

    messageBusConnection.subscribe(DeviceHeadsUpListener.TOPIC, MyDeviceHeadsUpListener())
  }

  override fun dispose() {
    deviceClientRegistry.removeListener(this)
    contentManagers.clear()
    onToolWindowHidden()
    RunningEmulatorCatalog.getInstance().removeListener(this)
  }

  @AnyThread
  private fun onDeviceHeadsUp(serialNumber: String, activation: ActivationLevel, project: Project) {
    if (project == toolWindow.project) {
      UIUtil.invokeLaterIfNeeded {
        val excludedDevice = devicesExcludedFromMirroring.remove(serialNumber)
        when {
          excludedDevice != null -> activateMirroring(serialNumber, excludedDevice.handle, excludedDevice.config, activation)
          serialNumber in deviceClients -> onPhysicalDeviceHeadsUp(serialNumber, activation)
          else -> addAttentionRequestAndTriggerEmulatorCatalogUpdate(serialNumber, activation)
        }
      }
    }
  }

  private fun addAttentionRequestAndTriggerEmulatorCatalogUpdate(serialNumber: String, activation: ActivationLevel) {
    recentAttentionRequests.put(serialNumber, activation)
    alarm.addRequest(recentAttentionRequests::cleanUp, ATTENTION_REQUEST_EXPIRATION.inWholeMicroseconds)
    if (isLocalEmulator(serialNumber)) {
      val deferred = RunningEmulatorCatalog.getInstance().updateNow()
      toolWindowScope.launch(Dispatchers.EDT) {
        try {
          val emulators = deferred.await()
          onEmulatorHeadsUp(serialNumber, emulators, activation)
        }
        catch (_: Exception) {
        }
      }
    }
  }

  private fun onPhysicalDeviceHeadsUp(serialNumber: String, activation: ActivationLevel) {
    val content = findContentBySerialNumberOfPhysicalDevice(serialNumber)
    content?.select(activation) ?: recentAttentionRequests.put(serialNumber, activation)
    toolWindow.activate(activation)
  }

  private fun onEmulatorHeadsUp(serialNumber: String, runningEmulators: Set<EmulatorController>, activation: ActivationLevel) {
    val emulator = runningEmulators.find { it.emulatorId.serialNumber == serialNumber } ?: return
    // Ignore standalone emulators.
    if (emulator.emulatorId.isEmbedded) {
      onEmulatorHeadsUp(emulator.emulatorId.avdId, activation)
    }
  }

  private fun onEmulatorHeadsUp(avdId: String, activation: ActivationLevel) {
    toolWindow.activate(activation)

    val content = findContentByAvdId(avdId)
    if (content == null) {
      RunningEmulatorCatalog.getInstance().updateNow()
      recentAvdLaunches.put(avdId, activation)
      alarm.addRequest(recentAvdLaunches::cleanUp, ATTENTION_REQUEST_EXPIRATION.inWholeMicroseconds)
    }
    else {
      content.select(activation)
    }
  }

  private fun onToolWindowShown() {
    if (!initialized) {
      initialized = true

      val newTabAction = NewTabAction()
      newTabAction.registerCustomShortcutSet(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK, toolWindow.component)
      toolWindow.setTabActions(newTabAction)

      val actionGroup = DefaultActionGroup()
      actionGroup.addAction(ToggleZoomToolbarAction())
      actionGroup.addAction(ToggleFloatingXrToolbarAction())
      actionGroup.addAction(ToggleDeviceFrameAction())
      toolWindow.setAdditionalGearActions(actionGroup)
      adoptContentManager(toolWindow.contentManager)
    }

    if (contentShown) {
      return
    }
    contentShown = true

    val emulatorCatalog = RunningEmulatorCatalog.getInstance()
    emulatorCatalog.updateNow()
    emulatorCatalog.addListener(this, EMULATOR_DISCOVERY_INTERVAL_MILLIS)
    assert(emulators.isEmpty())
    emulators.addAll(emulatorCatalog.emulators.filter { it.emulatorId.isEmbedded }) // Ignore standalone emulators.

    for (emulator in emulators) {
      if (findContentByEmulatorId(emulator.emulatorId) == null) {
        addEmulatorPanel(emulator)
      }
    }

    deviceClientRegistry.forEachClient { deviceClient ->
      val serialNumber = deviceClient.deviceSerialNumber
      if (serialNumber !in deviceClients) {
        val handle = onlineDevices[serialNumber]?.handle
        if (handle != null) {
          adoptDeviceClient(serialNumber, handle) { deviceClient }
        }
      }
    }

    for ((client, handle) in deviceClients.values) {
      if (findContentBySerialNumberOfPhysicalDevice(client.deviceSerialNumber) == null) {
        addPanel(DeviceToolWindowPanel(toolWindow.disposable, project, handle, client))
      }
    }

    for (contentManager in contentManagers) {
      // Remove panels of inactive devices.
      for (content in contentManager.contents) {
        val deviceId = content.deviceId ?: continue
        when (deviceId) {
          is DeviceId.EmulatorDeviceId -> {
            val emulator = emulators.find { it.emulatorId == deviceId.emulatorId }
            if (emulator == null || emulator.isShuttingDown) {
              savedUiState.remove(deviceId)
              content.removeAndDispose()
            }
          }

          is DeviceId.PhysicalDeviceId -> {
            val clientWithHandle = deviceClients[deviceId.serialNumber]
            if (clientWithHandle == null) {
              savedUiState.remove(deviceId)
              content.removeAndDispose()
            }
          }
        }
      }
      // Restore content of visible panels.
      for (content in contentManager.selectedContents) {
        val panel = content.component as? StreamingDevicePanel<*> ?: continue
        if (!panel.hasContent) {
          panel.createContent(deviceFrameVisible, savedUiState[panel.id])
        }
      }
    }

    if (contentManagers.size == 1 && contentManagers[0].contentCount == 0) {
      createEmptyStatePanel()
    }

    viewSelectionChanged()
  }

  private fun onToolWindowHidden() {
    if (!contentShown) {
      return
    }
    contentShown = false

    RunningEmulatorCatalog.getInstance().addListener(this, Long.MAX_VALUE) // Don't need frequent updates.
    for (emulator in emulators) {
      emulator.removeConnectionStateListener(connectionStateListener)
    }
    emulators.clear()
    recentAttentionRequests.invalidateAll()
    recentAvdLaunches.invalidateAll()

    for (contentManager in contentManagers) {
      val panel = contentManager.selectedContent?.component as? StreamingDevicePanel<*> ?: continue
      savedUiState[panel.id] = panel.destroyContent()
    }
  }

  private fun adoptContentManager(contentManager: ContentManager) {
    if (contentManager !in contentManagers) {
      contentManagers.add(contentManager)
      contentManager.addContentManagerListener(contentManagerListener)
      contentManager.addSelectedPanelDataProvider()
      Disposer.register(contentManager) {
        contentManagers.remove(contentManager)
        // When the tool window switches from a split to a non-split state by dragging a tab,
        // ToolWindowContentUi.update is not called after component tree takes its final shape.
        // This causes the tool window name to become visible when it should be hidden.
        // To compensate for that we trigger a layout update explicitly.
        toolWindow.updateContentUi()
      }
    }
  }

  private fun addEmulatorPanel(emulator: EmulatorController) {
    emulator.addConnectionStateListener(connectionStateListener)
    val avdId = emulator.emulatorId.avdId
    val contentManager = recentAvdStartRequesters.remove(avdId)
    addPanel(EmulatorToolWindowPanel(toolWindow.disposable, project, emulator), contentManager)
  }

  /**
   * Adds a device tab by adding [panel] to [targetContentManager] or to the main tool window
   * content manager if [targetContentManager] is null. Returns the added [Content] object or null
   * in case of an error.
   */
  private fun addPanel(panel: StreamingDevicePanel<*>, targetContentManager: ContentManager? = null): Content? {
    val contentManager = targetContentManager ?: toolWindow.contentManager
    val placeholderContent = contentManager.placeholderContent

    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(panel, shortenTitleText(panel.title), false).apply {
      putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
      tabName = panel.title
      description = panel.description
      icon = panel.icon
      popupIcon = panel.icon
      deviceId = panel.id
      setPreferredFocusedComponent(panel::preferredFocusableComponent)
    }

    panel.zoomToolbarVisible = zoomToolbarIsVisible

    if (findContentByDeviceId(panel.id) != null) {
      reportDuplicatePanel(content)
      return null
    }

    // Add panel to the end.
    contentManager.addContent(content)

    if (!content.isSelected) {
      val deviceId = panel.id
      val activation = max(
          recentAttentionRequests.remove(deviceId.serialNumber) ?: ActivationLevel.CREATE_TAB,
          (deviceId as? DeviceId.EmulatorDeviceId)?.emulatorId?.avdId?.let(recentAvdLaunches::remove) ?: ActivationLevel.CREATE_TAB)
      if (activation >= ActivationLevel.SELECT_TAB) {
        content.select(activation)
      }
    }

    placeholderContent?.removeAndDispose() // Remove the placeholder panel.

    setLiveIndicator(true)
    hideToolWindowName()

    return content
  }

  private fun reportDuplicatePanel(content: Content) {
    logger.error("An attempt to add a duplicate panel ${content.simpleId} ${content.displayName}")
  }

  private fun removeEmulatorPanel(emulator: EmulatorController): Boolean {
    emulator.removeConnectionStateListener(connectionStateListener)
    val content = findContentByEmulatorId(emulator.emulatorId) ?: return false
    savedUiState.remove(content.deviceId)
    content.removeAndDispose()
    return true
  }

  private fun removePhysicalDevicePanel(serialNumber: String) {
    deviceClients.remove(serialNumber)?.let {
      deviceClientRegistry.removeDeviceClient(serialNumber, this@StreamingToolWindowManager)
      updateMirroringHandlesFlow()
    }
    val content = findContentBySerialNumberOfPhysicalDevice(serialNumber) ?: return
    savedUiState.remove(content.deviceId)
    content.removeAndDispose()
  }

  private fun createEmptyStatePanel() {
    val panel = try {
      EmptyStatePanel(project, this)
    }
    catch (_: IncorrectOperationException) {
      // This object has been disposed already.
      return
    }
    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(panel, null, false).apply {
      isCloseable = false
    }
    val contentManager = toolWindow.contentManager
    try {
      contentManager.addContent(content)
      contentManager.setSelectedContent(content)
      showToolWindowName()
    }
    catch (_: IncorrectOperationException) {
      // Content manager has been disposed already.
      Disposer.dispose(content)
    }
  }

  private fun viewSelectionChanged() {
    for (contentManager in contentManagers) {
      for (i in 0 until contentManager.contentCount) {
        val content = contentManager.getContent(i) ?: break
        val panel = content.component
        if (panel is StreamingDevicePanel<*>) {
          if (content.isSelected) {
            if (!panel.hasContent) {
              // The panel became visible - create its content.
              panel.createContent(deviceFrameVisible, savedUiState.remove(panel.id))
              // Synchronize toolbar visibility across panels.
              ToggleToolbarAction.setToolbarVisible(toolWindow, PropertiesComponent.getInstance(project), null)
            }
          }
          else {
            if (panel.hasContent) {
              // The panel is no longer visible - destroy its content.
              savedUiState[panel.id] = panel.destroyContent()
            }
          }
        }
      }
    }
  }

  private fun findContentByDeviceId(deviceId: DeviceId): Content? =
      findContent { it.deviceId == deviceId }
  private fun findContentByEmulatorId(emulatorId: EmulatorId): Content? =
      findContent { (it.deviceId as? DeviceId.EmulatorDeviceId)?.emulatorId == emulatorId }
  private fun findContentByAvdId(avdId: String): Content? =
      findContent { (it.deviceId as? DeviceId.EmulatorDeviceId)?.emulatorId?.avdId == avdId }
  private fun findContentBySerialNumberOfPhysicalDevice(serialNumber: String): Content? =
      findContent { it.deviceId?.serialNumber == serialNumber && it.component is DeviceToolWindowPanel}

  private fun findContent(predicate: (Content) -> Boolean): Content? {
    for (contentManager in contentManagers) {
      for (i in 0 until contentManager.contentCount) {
        val content = contentManager.getContent(i) ?: break
        if (predicate(content)) {
          return content
        }
      }
    }
    return null
  }

  private fun updateLiveIndicator() {
    val embeddedEmulatorsRunning = RunningEmulatorCatalog.getInstance().emulators.find { it.emulatorId.isEmbedded } != null
    setLiveIndicator(embeddedEmulatorsRunning || !deviceClientRegistry.isEmpty())
  }

  private fun setLiveIndicator(live: Boolean) {
    toolWindow.setIcon(if (live) LIVE_ICON else INACTIVE_ICON)
  }

  private fun showToolWindowName() {
    if (StudioFlags.RUNNING_DEVICES_HIDE_TOOL_WINDOW_NAME.get()) {
      toolWindow.decorator.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, null)
      toolWindow.updateContentUi()
    }
  }

  private fun hideToolWindowName() {
    if (StudioFlags.RUNNING_DEVICES_HIDE_TOOL_WINDOW_NAME.get()) {
      toolWindow.decorator.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
      toolWindow.updateContentUi()
    }
  }

  @AnyThread
  override fun emulatorAdded(emulator: EmulatorController) {
    if (emulator.emulatorId.isEmbedded && emulator.emulatorConfig.isValid) {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (contentShown && emulators.add(emulator)) {
          addEmulatorPanel(emulator)
        }
      }
    }
  }

  @AnyThread
  override fun emulatorRemoved(emulator: EmulatorController) {
    if (emulator.emulatorId.isEmbedded) {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (removeEmulatorPanel(emulator)) {
          emulators.remove(emulator)
        }
      }
    }
  }

  override fun deviceClientAdded(client: DeviceClient, requester: Any?) {
    if (requester != this) {
      val serialNumber = client.deviceSerialNumber
      if (findContentBySerialNumberOfPhysicalDevice(serialNumber) == null) {
        val handle = onlineDevices[serialNumber]?.handle ?: return
        adoptDeviceClient(serialNumber, handle) { client }
        startMirroring(serialNumber, handle, client.deviceConfig, ActivationLevel.CREATE_TAB)
      }
    }
  }

  override fun deviceClientRemoved(client: DeviceClient, requester: Any?) {
    val serialNumber = client.deviceSerialNumber
    if (requester != this && deviceClients[serialNumber]?.client == client) {
      deactivateMirroring(serialNumber)
      deviceClients.remove(serialNumber)
    }
    else {
      updateLiveIndicator()
    }
  }

  private fun panelClosed(panel: DeviceToolWindowPanel) {
    val deactivationAction = when {
      isLocalEmulator(panel.deviceSerialNumber) -> null // Don't stop the mirrored standalone emulator.
      else -> panel.deviceHandle.deactivationAction
    }
    deactivationAction?.let { CoroutineScope(Dispatchers.IO).launch { it.deactivate() } } ?: stopMirroring(panel.deviceSerialNumber)
  }

  private fun deactivateMirroring(serialNumber: String) {
    findContentBySerialNumberOfPhysicalDevice(serialNumber)?.removeAndDispose()
    stopMirroring(serialNumber)
  }

  private fun stopMirroring(serialNumber: String) {
    deviceClients.remove(serialNumber)?.let {
      devicesExcludedFromMirroring[serialNumber] = DeviceDescription(it.client.deviceName, serialNumber, it.handle, it.client.deviceConfig)
      deviceClientRegistry.removeDeviceClient(serialNumber, this@StreamingToolWindowManager)
      updateMirroringHandlesFlow()
    }
  }

  private fun ToolWindow.activate(activation: ActivationLevel) {
    if (isVisible) {
      if (activation >= ActivationLevel.ACTIVATE_TAB) {
        activate(null)
      }
    }
    else {
      show {
        if (activation >= ActivationLevel.ACTIVATE_TAB) {
          activate(null)
        }
      }
    }
  }

  private fun activateMirroring(deviceDescription: DeviceDescription, contentManager: ContentManager? = null) {
    val serialNumber = deviceDescription.serialNumber
    if (serialNumber !in deviceClients) {
      startMirroringIfConfirmed(
          serialNumber, deviceDescription.handle, deviceDescription.config, ActivationLevel.ACTIVATE_TAB, contentManager)
    }
  }

  private fun activateMirroring(serialNumber: String, handle: DeviceHandle, config: DeviceConfiguration, activation: ActivationLevel) {
    if (contentShown) {
      val contentManager: ContentManager? = when {
        handle.reservationAction == null -> null
        else -> recentRemoteDeviceRequesters.remove(handle)
      }
      recentAttentionRequests.invalidate(serialNumber)
      if (serialNumber !in deviceClients && serialNumber !in devicesExcludedFromMirroring) {
        startMirroringIfConfirmed(serialNumber, handle, config, activation, contentManager)
      }
      else if (activation >= ActivationLevel.SELECT_TAB) {
        onPhysicalDeviceHeadsUp(serialNumber, activation)
      }
    }
    else if (activation >= ActivationLevel.SHOW_TOOL_WINDOW) {
      startMirroringIfConfirmed(serialNumber, handle, config, activation)
      toolWindow.activate(activation)
    }
  }

  private fun startMirroringIfConfirmed(serialNumber: String, handle: DeviceHandle, config: DeviceConfiguration,
                                        activation: ActivationLevel, contentManager: ContentManager? = null) {
    // Reservable devices are assumed to be privacy protected.
    if (deviceMirroringSettings.confirmationDialogShown || handle.reservationAction != null) {
      startMirroring(serialNumber, handle, config, activation, contentManager)
    }
    else if (!mirroringConfirmationDialogShowing) { // Ignore a recursive call inside the dialog's event loop.
      mirroringConfirmationDialogShowing = true
      val title = "About to Start Mirroring of ${config.deviceName}"
      val exitCode = MirroringConfirmationDialog(title).createWrapper(project).apply { show() }.exitCode
      mirroringConfirmationDialogShowing = false
      if (exitCode == MirroringConfirmationDialog.ACCEPT_EXIT_CODE) {
        deviceMirroringSettings.confirmationDialogShown = true
        startMirroring(serialNumber, handle, config, activation, contentManager)
      }
    }
  }

  private fun startMirroring(serialNumber: String, deviceHandle: DeviceHandle, config: DeviceConfiguration, activation: ActivationLevel,
                             contentManager: ContentManager? = null) {
    val deviceClient = getOrCreateDeviceClient(serialNumber, deviceHandle, config)
    startMirroring(serialNumber, deviceClient, deviceHandle, activation, contentManager)
  }

  private fun startMirroring(serialNumber: String, deviceClient: DeviceClient, deviceHandle: DeviceHandle, activation: ActivationLevel,
                             contentManager: ContentManager? = null) {
    if (serialNumber in onlineDevices) {
      if (contentShown) {
        updateMirroringHandlesFlow()
        deviceClient.establishAgentConnectionWithoutVideoStreamAsync(project) // Start the agent and connect to it proactively.
        val panel = DeviceToolWindowPanel(toolWindow.disposable, project, deviceHandle, deviceClient)
        val content = addPanel(panel, contentManager)
        if (activation >= ActivationLevel.SELECT_TAB && content != null) {
          content.select(activation)
        }
      }
      if (activation >= ActivationLevel.SHOW_TOOL_WINDOW) {
        if (!contentShown) {
          recentAttentionRequests.put(serialNumber, activation)
        }
        toolWindow.activate(activation)
      }
    }
  }

  private fun updateMirroringHandlesFlow() {
    if (project.isDisposed) {
      return
    }
    val mirroringHandles = mutableMapOf<DeviceHandle, MirroringHandle>()
    for (device in devicesExcludedFromMirroring.values) {
      if (device.handle.reservationAction == null) {
        mirroringHandles[device.handle] = MirroringActivator(device)
      }
    }
    for ((client, handle) in deviceClients.values) {
      if (handle.reservationAction == null) {
        mirroringHandles[handle] = MirroringDeactivator(client.deviceSerialNumber)
      }
    }
    project.service<MirroringManager>().mirroringHandles.value = mirroringHandles
  }

  @AnyThread
  private fun deviceConnected(serialNumber: String, device: ConnectedDevice) {
    val config = DeviceConfiguration(device.state.properties, useTitleAsName = isLocalEmulator(serialNumber))
    UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
      deviceConnected(serialNumber, device.handle, config)
    }
  }

  private fun deviceConnected(serialNumber: String, deviceHandle: DeviceHandle, config: DeviceConfiguration) {
    if (serialNumber in onlineDevices && serialNumber !in deviceClients) {
      val activation = maxOf(maxOf(recentAttentionRequests.remove(serialNumber), recentDisconnections.remove(serialNumber)),
                             when {
                               deviceMirroringSettings.activateOnConnection -> ActivationLevel.SHOW_TOOL_WINDOW
                               deviceClientRegistry.containsClientFor(serialNumber) -> ActivationLevel.CREATE_TAB
                               else -> null
                             })
      if (activation == null) {
        // The device is excluded from mirroring.
        val deviceDescription = devicesExcludedFromMirroring[serialNumber]
        if (deviceDescription == null) {
          devicesExcludedFromMirroring[serialNumber] = DeviceDescription(config.deviceName, serialNumber, deviceHandle, config)
          updateMirroringHandlesFlow()
        }
      }
      else {
        activateMirroring(serialNumber, deviceHandle, config, activation)
      }
    }
  }

  private fun rememberDisconnectedDevice(serialNumber: String) {
    val content = findContentBySerialNumberOfPhysicalDevice(serialNumber) ?: return
    val activation = when {
      content.component.containsFocus() -> ActivationLevel.ACTIVATE_TAB
      content.isSelected -> ActivationLevel.SELECT_TAB
      else -> ActivationLevel.CREATE_TAB
    }
    recentDisconnections.put(serialNumber, activation)
    alarm.addRequest(recentDisconnections::cleanUp, AUTO_RECONNECTION_TIMEOUT.inWholeMicroseconds)
  }

  private fun getOrCreateDeviceClient(serialNumber: String, deviceHandle: DeviceHandle, config: DeviceConfiguration): DeviceClient {
    return adoptDeviceClient(serialNumber, deviceHandle) {
      deviceClientRegistry.getOrCreateDeviceClient(serialNumber, this@StreamingToolWindowManager) {
        DeviceClient(serialNumber, config, config.deviceProperties.primaryAbi.toString()).apply {
          establishAgentConnectionWithoutVideoStreamAsync(project)
        }
      }
    }.client
  }

  private fun adoptDeviceClient(
      serialNumber: String, deviceHandle: DeviceHandle, clientSupplier: Supplier<DeviceClient>): DeviceClientWithHandle {
    var clientWithHandle = deviceClients[serialNumber]
    if (clientWithHandle == null) {
      clientWithHandle = DeviceClientWithHandle(clientSupplier.get(), deviceHandle)
      deviceClients[serialNumber] = clientWithHandle
      devicesExcludedFromMirroring.remove(serialNumber)
      updateMirroringHandlesFlow()
    }
    return clientWithHandle
  }

  private suspend fun showDeviceActionPopup(anchorComponent: Component?, dataContext: DataContext) {
    val actionGroup = createDeviceActions()

    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null, actionGroup, dataContext, ActionSelectionAid.SPEEDSEARCH, true, null, -1, null,
        ActionPlaces.getActionGroupPopupPlace(ActionPlaces.TOOLWINDOW_TOOLBAR_BAR))

    if (anchorComponent == null) {
      popup.showInFocusCenter()
    }
    else {
      popup.showUnderneathOf(anchorComponent)
    }
    // Clear initial selection.
    (popup as? ListPopupImpl)?.list?.clearSelection()
  }

  private suspend fun createDeviceActions(): DefaultActionGroup {
    return DefaultActionGroup().apply {
      val deviceDescriptions = devicesExcludedFromMirroring.values.toTypedArray().sortedBy { it.deviceName }
      if (deviceDescriptions.isNotEmpty()) {
        add(Separator("Connected Devices"))
        for (deviceDescription in deviceDescriptions) {
          add(StartDeviceMirroringAction(deviceDescription))
        }
        add(Separator.getInstance())
      }

      val reservedRemoteDevices = deviceProvisioner.reservedAndStartableDevices()
      if (reservedRemoteDevices.isNotEmpty()) {
        add(Separator("Reserved Remote Devices"))
        for (device in reservedRemoteDevices) {
          add(StartRemoteDeviceAction(device))
        }
        add(Separator.getInstance())
      }

      val remoteDevices = deviceProvisioner.reservableDevices()
      if (remoteDevices.isNotEmpty()) {
        add(Separator("Remote Devices"))
        for (template in remoteDevices) {
          add(ReserveRemoteDeviceAction(template))
        }
        add(Separator.getInstance())
      }

      val avds = getStartableVirtualDevices().sortedBy { it.displayNameWithApi }
      if (avds.isNotEmpty()) {
        add(Separator("Virtual Devices"))
        for (avd in avds) {
          add(StartAvdAction(avd, project))
        }
        add(Separator.getInstance())
      }

      add(ActionManager.getInstance().getAction(PairDevicesUsingWiFiAction.ID))
    }
  }

  private suspend fun getStartableVirtualDevices(): List<AvdInfo> {
    return withContext(Dispatchers.IO) {
      val runningAvdFolders = RunningEmulatorCatalog.getInstance().emulators.map { it.emulatorId.avdFolder }.toSet()
      val avdManager = AvdManagerConnection.getDefaultAvdManagerConnection()
      avdManager.getAvds(false).filter {
        it.dataFolderPath !in runningAvdFolders && (StudioFlags.EMBEDDED_EMULATOR_ALLOW_XR_AVD.get() || !it.isXrDevice)
      }
    }
  }

  @AnyThread
  private inner class MyDeviceHeadsUpListener : DeviceHeadsUpListener {

    override fun userInvolvementRequired(deviceSerialNumber: String, project: Project) {
      onDeviceHeadsUp(deviceSerialNumber, ActivationLevel.ACTIVATE_TAB, project)
    }

    override fun launchingApp(deviceSerialNumber: String, project: Project) {
      val activate = if (isNonMirrorableLocalEmulator(deviceSerialNumber)) emulatorSettings.activateOnAppLaunch
                     else deviceMirroringSettings.activateOnAppLaunch
      if (activate) {
        onDeviceHeadsUp(deviceSerialNumber, ActivationLevel.SELECT_TAB, project)
      }
    }

    override fun launchingTest(deviceSerialNumber: String, project: Project) {
      val activate = if (isNonMirrorableLocalEmulator(deviceSerialNumber)) emulatorSettings.activateOnTestLaunch
                     else deviceMirroringSettings.activateOnTestLaunch
      if (activate) {
        onDeviceHeadsUp(deviceSerialNumber, ActivationLevel.SELECT_TAB, project)
      }
    }

    private fun isNonMirrorableLocalEmulator(deviceSerialNumber: String): Boolean {
      return isLocalEmulator(deviceSerialNumber) &&
             deviceSerialNumber !in deviceClients && deviceSerialNumber !in devicesExcludedFromMirroring
    }
  }

  private inner class ToggleDeviceFrameAction : ToggleAction("Show Device Frame"), DumbAware {

    override fun update(event: AnActionEvent) {
      super.update(event)
      event.presentation.isEnabledAndVisible =
          findContent { content ->
            (content.component as? EmulatorToolWindowPanel).let { it?.emulator?.emulatorConfig?.skinFolder != null && it.hasContent }
          } != null
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

    override fun update(event: AnActionEvent) {
      super.update(event)
      // Enabled only for non-XR devices.
      event.presentation.isEnabledAndVisible = event.toolWindowContents.find { it.isSelected && it.component.isNonXrDevicePanel() } != null
    }

    private fun JComponent.isNonXrDevicePanel(): Boolean =
        this is StreamingDevicePanel<*> && deviceType != DeviceType.XR

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private inner class PhysicalDeviceWatcher(disposableParent: Disposable) : Disposable {
    private val coroutineScope: CoroutineScope

    init {
      Disposer.register(disposableParent, this)
      val executor = createBoundedApplicationPoolExecutor("EmulatorToolWindowManager.PhysicalDeviceWatcher", 1)
      coroutineScope = createCoroutineScope(executor.asCoroutineDispatcher())
      coroutineScope.launch {
        deviceProvisioner.mirrorableDevicesBySerialNumber().collect { newOnlineDevices ->
          UIUtil.invokeLaterIfNeeded {
            onlineDevices = newOnlineDevices
            onlineDevicesChanged()
          }
        }
      }
    }

    @UiThread
    private fun onlineDevicesChanged() {
      val removedExcluded = devicesExcludedFromMirroring.keys.retainAll(onlineDevices.keys)
      val removed = deviceClients.keys.minus(onlineDevices.keys)
      if (removed.isNotEmpty()) {
        if (contentShown) {
          for (device in removed) {
            rememberDisconnectedDevice(device)
            removePhysicalDevicePanel(device)
          }
        }
        else {
          deviceClients.keys.removeAll(removed)
        }
      }

      if (removedExcluded || removed.isNotEmpty()) {
        updateMirroringHandlesFlow()
      }

      for ((serialNumber, device) in onlineDevices) {
        if (serialNumber !in deviceClients && serialNumber !in devicesExcludedFromMirroring) {
          coroutineScope.launch {
            deviceConnected(serialNumber, device)
          }
        }
      }

      if (!contentShown) {
        toolWindowScope.launch(Dispatchers.IO) {
          RunningEmulatorCatalog.getInstance().updateNow().await()
          withContext(Dispatchers.EDT) {
            updateLiveIndicator()
          }
        }
      }
    }

    override fun dispose() {
      deviceClients.clear()
      updateMirroringHandlesFlow()
    }
  }

  private data class DeviceClientWithHandle(val client: DeviceClient, val handle: DeviceHandle)

  private inner class NewTabAction : DumbAwareAction("Add Device", "Show a new device", AllIcons.General.Add), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
      val component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
      val actionComponent = if (component is ActionButtonComponent) component else event.findComponentForAction(this)
      val dataContext = event.dataContext

      toolWindowScope.launch {
        showDeviceActionPopup(actionComponent, dataContext)
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private inner class StartDeviceMirroringAction(
    private val device: DeviceDescription,
  ) : DumbAwareAction(device.deviceName, null, device.config.deviceProperties.icon) {

    override fun actionPerformed(event: AnActionEvent) {
      activateMirroring(device, event.contentManager)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private inner class StartRemoteDeviceAction(
    private val device: DeviceHandle,
  ) : DumbAwareAction(device.sourceTemplate?.properties?.composeDeviceName(), null, device.sourceTemplate?.properties?.icon) {

    override fun actionPerformed(event: AnActionEvent) {
      val contentManager = event.contentManager
      if (contentManager != null) {
        recentRemoteDeviceRequesters.put(device, contentManager)
        alarm.addRequest(recentRemoteDeviceRequesters::cleanUp, REMOTE_DEVICE_REQUEST_EXPIRATION.inWholeMicroseconds)
      }
      device.scope.launch { device.activationAction?.activate() }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private inner class ReserveRemoteDeviceAction(
      private val template: DeviceTemplate
  ): DumbAwareAction(template.properties.composeDeviceName(), null, template.properties.icon) {
    override fun actionPerformed(e: AnActionEvent) {
      val childScope = toolWindowScope.createChildScope(true)
      template.launchCatchingDeviceActionException(childScope) { activationAction.activate() }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private inner class StartAvdAction(
    private val avd: AvdInfo,
    private val project: Project,
  ) : DumbAwareAction(avd.displayNameWithApi, null, avd.icon) {

    override fun actionPerformed(event: AnActionEvent) {
      val contentManager = event.contentManager
      if (contentManager != null) {
        recentAvdStartRequesters.put(avd.id, contentManager)
        alarm.addRequest(recentAvdStartRequesters::cleanUp, ATTENTION_REQUEST_EXPIRATION.inWholeMicroseconds)
      }
      toolWindowScope.launch(Dispatchers.IO) {
        val avdManager = AvdManagerConnection.getDefaultAvdManagerConnection()
        try {
          avdManager.startAvd(project, avd, RequestType.DIRECT_RUNNING_DEVICES)
        }
        catch (e: Exception) {
          val message = e.message?.let { if (it.contains(avd.displayName)) it else "Unable to launch ${avd.displayName} - $it"} ?:
              "Unable to launch ${avd.displayName}"
          withContext(Dispatchers.EDT) {
            showErrorDialog(toolWindow.component, message)
          }
        }
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private inner class MirroringActivator(private val device: DeviceDescription) : MirroringHandle {

    override val mirroringState: MirroringState
      get() = MirroringState.INACTIVE

    override fun toggleMirroring() {
      activateMirroring(device)
    }

    override fun toString(): String {
      return "MirroringActivator for ${device.serialNumber}"
    }
  }

  private inner class MirroringDeactivator(private val serialNumber: String) : MirroringHandle {

    override val mirroringState: MirroringState
      get() = MirroringState.ACTIVE

    override fun toggleMirroring() {
      deactivateMirroring(serialNumber)
    }

    override fun toString(): String {
      return "MirroringDeactivator for $serialNumber"
    }
  }

  private class DeviceDescription(val deviceName: String, val serialNumber: String, val handle: DeviceHandle,
                                  val config: DeviceConfiguration)
}

private val logger = Logger.getInstance(StreamingToolWindowManager::class.java)

private class ConnectedDevice(val handle: DeviceHandle, val state: DeviceState.Connected)

private fun DeviceProvisioner.mirrorableDevicesBySerialNumber(): Flow<Map<String, ConnectedDevice>> {
  return connectedDevices().map { connectedDevices ->
    connectedDevices.filter { it.state.isMirrorable() }.associateBy { it.state.serialNumber }
  }
}

private fun DeviceProvisioner.connectedDevices(): Flow<List<ConnectedDevice>> {
  return mapStateNotNull { handle, state -> (state as? DeviceState.Connected)?.let { ConnectedDevice(handle, it) } }
}

private fun DeviceProvisioner.reservedAndStartableDevices(): List<DeviceHandle> {
  return devices.value.filter {
    it.state.reservation?.state == ReservationState.ACTIVE && it.activationAction?.presentation?.value?.enabled == true
  }.sortedBy { it.state.properties.composeDeviceName() }
}

private fun DeviceProvisioner.reservableDevices(): List<DeviceTemplate> {
  return templates.value.filter {
    it.properties.isRemote == true && it.activationAction.presentation.value.enabled
  }.sortedBy { it.properties.composeDeviceName() }
}

private suspend fun DeviceState.Connected.isMirrorable(): Boolean {
  if (!isOnline()) {
    return false
  }

  val deviceSerialNumber = serialNumber
  when {
    isLocalEmulator(deviceSerialNumber) -> { // Local virtual device.
      if (!StudioFlags.DEVICE_MIRRORING_STANDALONE_EMULATORS.get()) {
        return false
      }
      val emulators = RunningEmulatorCatalog.getInstance().updateNow().await()
      val emulator = emulators.find { "emulator-${it.emulatorId.serialPort}" == deviceSerialNumber }
      if (emulator == null || emulator.emulatorId.isEmbedded) {
        return false
      }
    }
    properties.isVirtual == true -> { // Remote virtual device.
      if (!StudioFlags.DEVICE_MIRRORING_REMOTE_EMULATORS.get()) {
        return false
      }
    }
  }

  val apiLevel = properties.androidVersion?.androidApiLevel?.majorVersion ?: SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
  // Mirroring is supported for API >= 26. Wear OS devices with API < 30 don't support VP8/VP9 video encoders.
  return apiLevel >= 26 && (properties.deviceType != DeviceType.WEAR || apiLevel >= 30) && properties.primaryAbi != null
}

private val DeviceState.Connected.serialNumber: String
    get() = connectedDevice.serialNumber

private fun ContentManager.addSelectedPanelDataProvider() {
  addDataProvider(EdtNoGetDataProvider { sink -> DataSink.uiDataSnapshot(sink, selectedContent?.component) })
}

private val ContentManager.placeholderContent: Content?
  get() {
    val content = if (contentCount == 1) getContent(0) else return null
    return if (content?.deviceId == null) content else null
  }

private fun Content.select(activation: ActivationLevel) {
  manager?.setSelectedContent(this, activation >= ActivationLevel.ACTIVATE_TAB)
}

private fun Content.removeAndDispose() {
  manager?.removeContent(this, true)
}

private val AnActionEvent.contentManager: ContentManager?
  get() {
    val contentManager = getData(PlatformDataKeys.CONTENT_MANAGER)
    if (contentManager != null) {
      return contentManager
    }
    return getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)?.containingDecorator?.contentManager
  }

private val Component.containingDecorator: InternalDecorator?
  get() = ComponentUtil.getParentOfType(InternalDecorator::class.java, this)

private fun isLocalEmulator(deviceSerialNumber: String) =
    deviceSerialNumber.startsWith("emulator-")

private fun isEmbeddedEmulator(commandLine: GeneralCommandLine) =
    commandLine.parametersList.parameters.contains("-qt-hide-window")

private fun shortenTitleText(title: String): String =
    StringUtil.shortenTextWithEllipsis(title, 25, 6)

private enum class ActivationLevel {
  /** Create tab, but don't select it and don't show the tool window if hidden. */
  CREATE_TAB,
  /** Create tab and show the tool window if hidden. */
  SHOW_TOOL_WINDOW,
  /** Create tab, show the tool window if hidden and select the new tab. */
  SELECT_TAB,
  /** Create tab, show the tool window if hidden, select the new tab and focus on it. */
  ACTIVATE_TAB,
}

private fun maxOf(a: ActivationLevel?, b: ActivationLevel?): ActivationLevel? {
  return when {
    b == null -> a
    a == null -> b
    else -> kotlin.comparisons.maxOf(a, b)
  }
}

@Service(Service.Level.APP)
internal class DeviceClientRegistry : Disposable {

  private val clientsBySerialNumber = LinkedHashMap<String, DeviceClient>()
    /** The returned map may only be accessed on the UI thread. */
    @UiThread
    get
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<Listener>()

  /**
   * Returns the existing or a newly created client for the specified device. When a new client is
   * created, all listeners are notified by calling [Listener.deviceClientAdded].
   */
  @UiThread
  fun getOrCreateDeviceClient(serialNumber: String, requester: Any?, clientCreator: (serialNumber: String) -> DeviceClient): DeviceClient {
    return clientsBySerialNumber.computeIfAbsent(serialNumber) { serial ->
      clientCreator(serial).also { client ->
        Disposer.register(this, client)
        for (listener in listeners) {
          try {
            listener.deviceClientAdded(client, requester)
          }
          catch (e: Exception) {
            logger.error(e)
          }
        }
      }
    }
  }

  /**
   * Terminates mirroring of the device and deletes the client. All listeners are notified by
   * calling [Listener.deviceClientRemoved].
   */
  @UiThread
  fun removeDeviceClient(serialNumber: String, requester: Any?) {
    clientsBySerialNumber.remove(serialNumber)?.also { client ->
      for (listener in listeners) {
        try {
          listener.deviceClientRemoved(client, requester)
        }
        catch (e: Exception) {
          logger.error(e)
        }
      }
      Disposer.dispose(client)
    }
  }

  @UiThread
  fun containsClientFor(serialNumber: String): Boolean = clientsBySerialNumber.containsKey(serialNumber)

  /** Iterates over existing device clients. The passed in consumer should not create or delete clients. */
  @UiThread
  fun forEachClient(consumer: (DeviceClient) -> Unit) {
    for (client in clientsBySerialNumber.values) {
      consumer(client)
    }
  }

  @UiThread
  fun isEmpty(): Boolean =
      clientsBySerialNumber.isEmpty()

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  override fun dispose() {
  }

  /** Removes all device clients from the registry. */
  @TestOnly
  @UiThread
  fun clear() {
    for (serialNumber in clientsBySerialNumber.keys.toList()) {
      removeDeviceClient(serialNumber, null)
    }
  }

  interface Listener {

    @UiThread
    fun deviceClientAdded(client: DeviceClient, requester: Any?)

    @UiThread
    fun deviceClientRemoved(client: DeviceClient, requester: Any?)
  }
}

private fun <K : Any, V> buildCache(expiration: Duration): Cache<K, V> =
    Caffeine.newBuilder().expireAfterWrite(expiration.toJavaDuration()).build()
private fun <K : Any, V> buildWeakCache(expiration: Duration): Cache<K, V> =
    Caffeine.newBuilder().weakKeys().weakValues().expireAfterWrite(expiration.toJavaDuration()).build()
private fun <K : Any, V> Cache<K, V>.remove(key: K): V? = getIfPresent(key)?.also { invalidate(key) }

private var Content.deviceId: DeviceId?
    get() = CONTENT_DEVICE_ID_KEY.get(this)
    set(deviceId) = CONTENT_DEVICE_ID_KEY.set(this, deviceId)