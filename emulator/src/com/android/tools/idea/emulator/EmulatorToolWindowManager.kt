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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.IDevice
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.run.AppDeploymentListener
import com.google.common.cache.CacheBuilder
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.actions.ToggleToolbarAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.Alarm
import com.intellij.util.concurrency.EdtExecutorService
import icons.StudioIcons
import java.text.Collator
import java.time.Duration

/**
 * Manages contents of the Emulator tool window. Listens to changes in [RunningEmulatorCatalog]
 * and maintains [EmulatorToolWindowPanel]s, one per running Emulator.
 */
internal class EmulatorToolWindowManager private constructor(private val project: Project) : RunningEmulatorCatalog.Listener, DumbAware {
  private val ID_KEY = Key.create<EmulatorId>("emulator-id")

  private var contentCreated = false
  private val panels: MutableList<EmulatorToolWindowPanel> = arrayListOf()
  private var selectedPanel: EmulatorToolWindowPanel? = null
  /** When the tool window is hidden, the ID of the last selected Emulator, otherwise null. */
  private var lastSelectedEmulatorId: EmulatorId? = null
  private val emulators: MutableSet<EmulatorController> = hashSetOf()
  private val properties = PropertiesComponent.getInstance(project)
  // IDs of recently launched AVDs keyed by themselves.
  private val recentLaunches = CacheBuilder.newBuilder().expireAfterWrite(LAUNCH_INFO_EXPIRATION).build<String, String>()
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project.earlyDisposable)

  private val contentManagerListener = object : ContentManagerListener {
    @UiThread
    override fun selectionChanged(event: ContentManagerEvent) {
      viewSelectionChanged(getToolWindow())
    }

    @UiThread
    override fun contentRemoved(event: ContentManagerEvent) {
      val panel = event.content.component as? EmulatorToolWindowPanel ?: return
      panel.emulator.shutdown()

      panels.remove(panel)
      if (panels.isEmpty()) {
        createPlaceholderPanel()
        hideLiveIndicator(getToolWindow())
      }
    }
  }
  private val connectionStateListener = object: ConnectionStateListener {
    @AnyThread
    override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
      if (connectionState == ConnectionState.DISCONNECTED) {
        invokeLaterInAnyModalityState {
          if (contentCreated && emulators.remove(emulator)) {
            removeEmulatorPanel(emulator)
          }
        }
      }
    }
  }

  private var frameIsCropped
    get() = properties.getBoolean(FRAME_CROPPED_PROPERTY, FRAME_CROPPED_DEFAULT)
    set(value) {
      properties.setValue(FRAME_CROPPED_PROPERTY, value, FRAME_CROPPED_DEFAULT)
      for (panel in panels) {
        panel.setCropFrame(value)
      }
    }

  private var zoomToolbarIsVisible
    get() = properties.getBoolean(ZOOM_TOOLBAR_VISIBLE_PROPERTY, ZOOM_TOOLBAR_VISIBLE_DEFAULT)
    set(value) {
      properties.setValue(ZOOM_TOOLBAR_VISIBLE_PROPERTY, value, ZOOM_TOOLBAR_VISIBLE_DEFAULT)
      for (panel in panels) {
        panel.zoomToolbarIsVisible = value
      }
    }

  init {
    Disposer.register(project.earlyDisposable, Disposable {
      destroyContent(getToolWindow())
    })

    // Lazily initialize content since we can only have one frame.
    val messageBusConnection = project.messageBus.connect(project.earlyDisposable)
    messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      @UiThread
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow(EMULATOR_TOOL_WINDOW_ID) ?: return

        toolWindowManager.invokeLater(Runnable {
          if (!project.isDisposed) {
            if (toolWindow.isVisible) {
              createContent(toolWindow)
            }
            else {
              destroyContent(toolWindow)
            }
          }
        })
      }
    })

    messageBusConnection.subscribe(AvdLaunchListener.TOPIC,
                                   AvdLaunchListener { avd, commandLine, project ->
                                     if (project == this.project && isEmbeddedEmulator(commandLine)) {
                                       RunningEmulatorCatalog.getInstance().updateNow()
                                       invokeLaterInAnyModalityState { onEmulatorUsed(avd.name) }
                                     }
                                   })

    messageBusConnection.subscribe(AppDeploymentListener.TOPIC,
                                   AppDeploymentListener { device, project ->
                                     if (project == this.project && device.isEmulator) {
                                       onDeploymentToEmulator(device)
                                     }
                                   })
  }

  @AnyThread
  private fun onDeploymentToEmulator(device: IDevice) {
    val future = RunningEmulatorCatalog.getInstance().updateNow()
    future.addCallback(EdtExecutorService.getInstance(),
                       success = { emulators ->
                         if (emulators != null) {
                           onDeploymentToEmulator(device, emulators)
                         }},
                       failure = {})
  }

  private fun onDeploymentToEmulator(device: IDevice, runningEmulators: Set<EmulatorController>) {
    val serialPort = device.serialPort
    val emulator = runningEmulators.find { it.emulatorId.serialPort == serialPort } ?: return
    // Ignore standalone emulators.
    if (emulator.emulatorId.isEmbedded) {
      onEmulatorUsed(emulator.emulatorId.avdId)
    }
  }

  private fun onEmulatorUsed(avdId: String) {
    val toolWindow = getToolWindow()
    if (!toolWindow.isVisible) {
      toolWindow.show(null)
      if (!toolWindow.isActive) {
        toolWindow.activate(null)
      }
    }

    val panel = findPanelByAvdId(avdId)
    if (panel == null) {
      RunningEmulatorCatalog.getInstance().updateNow()
      recentLaunches.put(avdId, avdId)
      alarm.addRequest(recentLaunches::cleanUp, LAUNCH_INFO_EXPIRATION.toMillis())
    }
    else if (selectedPanel != panel) {
      val contentManager = toolWindow.contentManager
      val content = contentManager.getContent(panel.component)
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
    actionGroup.addAction(ToggleFrameCropAction())
    (toolWindow as ToolWindowEx).setAdditionalGearActions(actionGroup)

    val emulatorCatalog = RunningEmulatorCatalog.getInstance()
    emulatorCatalog.updateNow()
    emulatorCatalog.addListener(this, EMULATOR_DISCOVERY_INTERVAL_MILLIS)
    // Ignore standalone emulators.
    emulators.addAll(emulatorCatalog.emulators.asSequence().filter { it.emulatorId.isEmbedded })

    // Create the panel for the last selected Emulator before other panels so that it becomes selected
    // unless a recently launched Emulator takes over.
    val activeEmulator = lastSelectedEmulatorId?.let { emulators.find { it.emulatorId == lastSelectedEmulatorId } }
    lastSelectedEmulatorId = null // Not maintained when the tool window is visible.
    if (activeEmulator != null && !activeEmulator.isShuttingDown) {
      addEmulatorPanel(activeEmulator)
    }
    for (emulator in emulators) {
      if (emulator != activeEmulator && !emulator.isShuttingDown) {
        addEmulatorPanel(emulator)
      }
    }

    val contentManager = toolWindow.contentManager
    if (contentManager.contentCount == 0) {
      createPlaceholderPanel()
    }

    contentManager.addContentManagerListener(contentManagerListener)
    viewSelectionChanged(toolWindow)
  }

  private fun destroyContent(toolWindow: ToolWindow) {
    if (!contentCreated) {
      return
    }
    contentCreated = false

    lastSelectedEmulatorId = selectedPanel?.id
    RunningEmulatorCatalog.getInstance().removeListener(this)
    for (emulator in emulators) {
      emulator.removeConnectionStateListener(connectionStateListener)
    }
    emulators.clear()
    val contentManager = toolWindow.contentManager
    contentManager.removeContentManagerListener(contentManagerListener)
    contentManager.removeAllContents(true)
    selectedPanel?.destroyContent()
    selectedPanel = null
    panels.clear()
    recentLaunches.invalidateAll()
  }

  private fun addEmulatorPanel(emulator: EmulatorController) {
    emulator.addConnectionStateListener(connectionStateListener)

    val panel = EmulatorToolWindowPanel(emulator)
    val toolWindow = getToolWindow()
    val contentManager = toolWindow.contentManager
    if (panels.isEmpty()) {
      contentManager.removeAllContents(true) // Remove the placeholder panel.
      showLiveIndicator(toolWindow)
    }

    panel.zoomToolbarIsVisible = zoomToolbarIsVisible
    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(panel.component, panel.title, false).apply {
      putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
      isCloseable = true
      tabName = panel.title
      icon = panel.icon
      popupIcon = panel.icon
      putUserData(ID_KEY, panel.id)
      setPreferredFocusedComponent { panel.getPreferredFocusableComponent() }
    }

    val index = panels.binarySearch(panel, PANEL_COMPARATOR).inv()
    assert(index >= 0)

    if (index >= 0) {
      panels.add(index, panel)
      contentManager.addContent(content, index)

      if (selectedPanel != panel) {
        // Activate the newly added panel if it corresponds to a recently launched or used Emulator.
        val avdId = panel.id.avdId
        if (recentLaunches.getIfPresent(panel.id.avdId) != null) {
          recentLaunches.invalidate(avdId)
          contentManager.setSelectedContent(content)
        }
      }
    }
  }

  private fun removeEmulatorPanel(emulator: EmulatorController) {
    emulator.removeConnectionStateListener(connectionStateListener)

    val panel = findPanelByGrpcPort(emulator.emulatorId.grpcPort) ?: return

    val toolWindow = getToolWindow()
    val contentManager = toolWindow.contentManager
    val content = contentManager.getContent(panel.component)
    contentManager.removeContent(content, true)
  }

  private fun createPlaceholderPanel() {
    val panel = PlaceholderPanel(project)
    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(panel, panel.title, false).apply {
      tabName = panel.title
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
      selectedPanel?.destroyContent()
      selectedPanel = null

      if (id != null) {
        selectedPanel = findPanelByGrpcPort(id.grpcPort)
        selectedPanel?.createContent(frameIsCropped)
        ToggleToolbarAction.setToolbarVisible(toolWindow, PropertiesComponent.getInstance(project), null)
      }
    }
  }

  private fun findPanelByGrpcPort(grpcPort: Int): EmulatorToolWindowPanel? {
    return panels.firstOrNull { it.id.grpcPort == grpcPort }
  }

  private fun findPanelByAvdId(avdId: String): EmulatorToolWindowPanel? {
    return panels.firstOrNull { it.id.avdId == avdId }
  }

  private fun getContentManager(): ContentManager {
    return getToolWindow().contentManager
  }

  private fun getToolWindow(): ToolWindow {
    return ToolWindowManager.getInstance(project).getToolWindow(EMULATOR_TOOL_WINDOW_ID) ?:
           throw IllegalStateException("Could not find Emulator tool window")
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
      invokeLaterInAnyModalityState {
        if (contentCreated && emulators.add(emulator)) {
          addEmulatorPanel(emulator)
        }
      }
    }
  }

  @AnyThread
  override fun emulatorRemoved(emulator: EmulatorController) {
    if (emulator.emulatorId.isEmbedded) {
      invokeLaterInAnyModalityState {
        if (contentCreated && emulators.remove(emulator)) {
          removeEmulatorPanel(emulator)
        }
      }
    }
  }

  /**
   * Extracts and returns the port number from the serial number of an Emulator device,
   * or zero if the serial number doesn't have an expected format, "emulator-<port_number>".
   */
  private val IDevice.serialPort: Int
    get() {
      require(isEmulator)
      val pos = serialNumber.indexOf('-')
      return StringUtil.parseInt(serialNumber.substring(pos + 1), 0)
    }

  private inner class ToggleFrameCropAction : ToggleAction("Crop Device Frame"), DumbAware {
    override fun isSelected(event: AnActionEvent): Boolean {
      return frameIsCropped
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      frameIsCropped = state
    }
  }

  private inner class ToggleZoomToolbarAction : ToggleAction("Show Zoom Controls"), DumbAware {
    override fun isSelected(event: AnActionEvent): Boolean {
      return zoomToolbarIsVisible
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      zoomToolbarIsVisible = state
    }
  }

  companion object {
    private const val FRAME_CROPPED_PROPERTY = "com.android.tools.idea.emulator.frame.cropped"
    private const val FRAME_CROPPED_DEFAULT = false
    private const val ZOOM_TOOLBAR_VISIBLE_PROPERTY = "com.android.tools.idea.emulator.zoom.toolbar.visible"
    private const val ZOOM_TOOLBAR_VISIBLE_DEFAULT = true
    private const val EMULATOR_DISCOVERY_INTERVAL_MILLIS = 1000
    @JvmStatic
    private val LAUNCH_INFO_EXPIRATION = Duration.ofSeconds(30)

    @JvmStatic
    private val COLLATOR = Collator.getInstance()

    @JvmStatic
    private val PANEL_COMPARATOR = compareBy<EmulatorToolWindowPanel, Any?>(COLLATOR) { it.title }.thenBy { it.id.grpcPort }

    @JvmStatic
    private val registeredProjects: MutableSet<Project> = hashSetOf()

    /**
     * Initializes [EmulatorToolWindowManager] for the given project. Repeated calls for the same project
     * are ignored.
     */
    @JvmStatic
    fun initializeForProject(project: Project) {
      if (registeredProjects.add(project)) {
        Disposer.register(project.earlyDisposable, Disposable { registeredProjects.remove(project) })
        EmulatorToolWindowManager(project)
      }
    }

    @JvmStatic
    private fun isEmbeddedEmulator(commandLine: GeneralCommandLine) =
      commandLine.parametersList.parameters.contains("-qt-hide-window")
  }
}
