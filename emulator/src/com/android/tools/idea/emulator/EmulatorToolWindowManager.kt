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
import com.android.tools.idea.run.AppDeploymentListener
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.concurrency.addCallback
import com.google.common.cache.CacheBuilder
import com.intellij.execution.configurations.GeneralCommandLine
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
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.util.Alarm
import com.intellij.util.concurrency.EdtExecutorService
import java.text.Collator
import java.time.Duration

/**
 * Manages contents of the Emulator tool window. Listens to changes in [RunningEmulatorCatalog]
 * and maintains [EmulatorToolWindowPanel]s, one per running Emulator.
 */
internal class EmulatorToolWindowManager private constructor(private val project: Project) : RunningEmulatorCatalog.Listener, DumbAware {
  private val ID_KEY = Key.create<EmulatorId>("emulator-id")

  private var contentInitialized = false
  private val panels: MutableList<EmulatorToolWindowPanel> = arrayListOf()
  private var selectedPanel: EmulatorToolWindowPanel? = null
  /** When the tool window is hidden, the ID of the last selected Emulator, otherwise null. */
  private var lastSelectedEmulatorId: EmulatorId? = null
  private val emulators: MutableSet<EmulatorController> = hashSetOf()
  private val properties = PropertiesComponent.getInstance(project)
  // IDs of recently launched AVDs keyed by themselves.
  private val recentLaunches = CacheBuilder.newBuilder().expireAfterWrite(LAUNCH_INFO_EXPIRATION).build<String, String>()
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)

  private var contentManagerListener = object : ContentManagerAdapter() {
    @UiThread
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        viewSelectionChanged(event.source as ContentManager)
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
    // Lazily initialize content since we can only have one frame.
    val messageBusConnection = project.messageBus.connect(project)
    messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      @UiThread
      override fun stateChanged() {
        // We need to query the tool window again, because it might have been unregistered when closing the project.
        val toolWindow = getToolWindow()

        if (toolWindow.isVisible) {
          if (!contentInitialized) {
            createContent(toolWindow)
          }
        }
        else if (contentInitialized) {
          destroyContent(toolWindow)
        }
      }
    })

    messageBusConnection.subscribe(AvdLaunchListener.TOPIC,
                                   AvdLaunchListener { avd, commandLine, project ->
                                     if (project == this.project && isEmbeddedEmulator(commandLine)) {
                                       RunningEmulatorCatalog.getInstance().updateNow()
                                       invokeLater { onEmulatorUsed(avd.name) }
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
    onEmulatorUsed(emulator.emulatorId.avdId)
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
    contentInitialized = true

    val actionGroup = DefaultActionGroup()
    actionGroup.addAction(ToggleZoomToolbarAction())
    actionGroup.addAction(ToggleFrameCropAction())
    (toolWindow as ToolWindowEx).setAdditionalGearActions(actionGroup)

    val emulatorCatalog = RunningEmulatorCatalog.getInstance()
    emulatorCatalog.updateNow()
    emulatorCatalog.addListener(this, EMULATOR_DISCOVERY_INTERVAL_MILLIS)
    emulators.addAll(emulatorCatalog.emulators)
    // Create the panel for the last selected Emulator before other panels so that it becomes selected
    // unless a recently launched Emulator takes over.
    val activeEmulator = lastSelectedEmulatorId?.let { emulators.find { it.emulatorId == lastSelectedEmulatorId } }
    lastSelectedEmulatorId = null // Not maintained when the tool window is visible.
    if (activeEmulator != null) {
      addEmulatorPanel(activeEmulator)
    }
    for (emulator in emulators) {
      if (emulator != activeEmulator) {
        addEmulatorPanel(emulator)
      }
    }

    val contentManager = toolWindow.contentManager
    contentManager.addContentManagerListener(contentManagerListener)
    viewSelectionChanged(contentManager)
  }

  private fun destroyContent(toolWindow: ToolWindow) {
    lastSelectedEmulatorId = selectedPanel?.id
    contentInitialized = false
    RunningEmulatorCatalog.getInstance().removeListener(this)
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
    addEmulatorPanel(EmulatorToolWindowPanel(emulator))
  }

  private fun addEmulatorPanel(panel: EmulatorToolWindowPanel) {
    panel.zoomToolbarIsVisible = zoomToolbarIsVisible
    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(panel.component, panel.title, false)
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    content.tabName = panel.title
    content.icon = panel.icon
    content.popupIcon = panel.icon
    content.putUserData(ID_KEY, panel.id)

    val index = panels.binarySearch(panel, PANEL_COMPARATOR).inv()
    assert(index >= 0)

    if (index >= 0) {
      panels.add(index, panel)
      val contentManager = getContentManager()
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
    val panel = findPanelByGrpcPort(emulator.emulatorId.grpcPort)
    if (panel != null) {
      panels.remove(panel)
      val contentManager = getContentManager()
      val content = contentManager.getContent(panel.component)
      contentManager.removeContent(content, true)
    }
  }

  private fun viewSelectionChanged(contentManager: ContentManager) {
    val content = contentManager.selectedContent
    val id = content?.getUserData(ID_KEY)
    if (id != selectedPanel?.id) {
      selectedPanel?.destroyContent()
      selectedPanel = null

      if (id != null) {
        selectedPanel = findPanelByGrpcPort(id.grpcPort)
        selectedPanel?.createContent(frameIsCropped)
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
    return ToolWindowManager.getInstance(project).getToolWindow(ID) ?: throw IllegalStateException("Could not find Emulator tool window")
  }

  @AnyThread
  override fun emulatorAdded(emulator: EmulatorController) {
    invokeLater {
      if (contentInitialized && emulators.add(emulator)) {
        addEmulatorPanel(emulator)
      }
    }
  }

  @AnyThread
  override fun emulatorRemoved(emulator: EmulatorController) {
    invokeLater {
      if (contentInitialized && emulators.remove(emulator)) {
        removeEmulatorPanel(emulator)
      }
    }
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

  companion object {
    const val ID = "Emulator"

    private const val FRAME_CROPPED_PROPERTY = "com.android.tools.idea.emulator.frame.cropped"
    private const val FRAME_CROPPED_DEFAULT = true
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
        Disposer.register(project, Disposable { registeredProjects.remove(project) })
        EmulatorToolWindowManager(project)
      }
    }

    @JvmStatic
    private fun isEmbeddedEmulator(commandLine: GeneralCommandLine) =
      commandLine.parametersList.parameters.contains("-no-window")
  }
}
