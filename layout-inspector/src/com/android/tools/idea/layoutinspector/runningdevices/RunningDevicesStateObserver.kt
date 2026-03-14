/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.toolwindow.ContentManagerHierarchyAdapter
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.core.STREAMING_DEVICE_ID_KEY
import com.android.tools.idea.streaming.core.StreamingDeviceId
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

/** Class responsible for observing the state of Running Devices tabs. Can be used by other classes as source for Running Devices state. */
@UiThread
class RunningDevicesStateObserver(private val project: Project) : Disposable {

  interface Listener {
    /**
     * Called when the selected tabs in Running Devices change. There can be more than one selected tab if Running Deices is running in
     * split window mode.
     */
    fun onSelectedTabsChanged(selectedTabs: List<StreamingDeviceId>)

    /** Called when a tab is added or removed to Running Devices */
    fun onExistingTabsChanged(existingTabs: List<StreamingDeviceId>)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunningDevicesStateObserver {
      return project.getService(RunningDevicesStateObserver::class.java)
    }
  }

  private val listeners = mutableListOf<Listener>()

  private var selectedTabs: List<StreamingDeviceId> = emptyList()
    set(value) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (value == field) {
        return
      }

      field = value
      listeners.forEach { it.onSelectedTabsChanged(value) }
    }

  private var existingTabs = emptyList<StreamingDeviceId>()
    set(value) {
      if (value == field) {
        return
      }

      field = value
      listeners.forEach { it.onExistingTabsChanged(value) }
    }

  init {
    var toolWindowListener: RunningDevicesContentManagerListener? = null

    // Listen for changes to RD Tool Window state.
    val messageBusConnection = project.messageBus.connect(this)
    messageBusConnection.subscribe(
      ToolWindowManagerListener.TOPIC,
      object : ToolWindowManagerListener {

        override fun stateChanged(toolWindowManager: ToolWindowManager) {
          val toolWindow = toolWindowManager.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return

          if (toolWindowListener == null) {
            // Register the listener only once.
            toolWindowListener =
              RunningDevicesContentManagerListener(toolWindow).also { Disposer.register(this@RunningDevicesStateObserver, it) }
          }

          toolWindowManager.invokeLater {
            if (!toolWindow.isDisposed) {
              if (toolWindow.isVisible) {
                // Restore selected tabs that were removed when the tool window was hidden.
                updateSelectedTabs()
              } else {
                selectedTabs = emptyList()
              }
            }
          }
        }
      },
    )
  }

  override fun dispose() {}

  fun removeListener(listener: Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    listeners.remove(listener)
  }

  fun addListener(listener: Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    listener.onExistingTabsChanged(existingTabs)
    listener.onSelectedTabsChanged(selectedTabs)

    listeners.add(listener)
  }

  /** Returns a list of all content from Running Devices, across all existing ContentManagers. */
  private fun getAllContents(): List<Content> {
    val toolWindow =
      project.getServiceIfCreated(ToolWindowManager::class.java)?.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return emptyList()
    return toolWindow.contentManagerIfCreated?.contentsRecursively ?: emptyList()
  }

  fun getTabContent(streamingDeviceId: StreamingDeviceId): Content? {
    return getAllContents().find { it.streamingDeviceId == streamingDeviceId }
  }

  private fun updateSelectedTabs() {
    val deviceIds = getRunningDevicesSelectedTabs()
    selectedTabs = deviceIds
  }

  private fun updateExistingTabs() {
    val deviceIds = getAllTabsDeviceIds()
    existingTabs = deviceIds
  }

  /** [ContentManagerListener] used to observe the content of the Running Devices Tool Window. */
  private inner class RunningDevicesContentManagerListener(toolWindow: ToolWindow) : ContentManagerHierarchyAdapter(toolWindow) {
    init {
      invokeLater {
        updateExistingTabs()
        updateSelectedTabs()
      }
    }

    override fun contentAdded(event: ContentManagerEvent) {
      // listeners are executed in order, if listeners before this one launched calls using
      // invokeLater, they should be executed first.
      invokeLater { updateExistingTabs() }
    }

    override fun contentRemoveQuery(event: ContentManagerEvent) {
      // listeners are executed in order, if listeners before this one launched calls using
      // invokeLater, they should be executed first.
      invokeLater { updateExistingTabs() }
    }

    override fun selectionChanged(event: ContentManagerEvent) {
      // listeners are executed in order, if listeners before this one launched calls using
      // invokeLater, they should be executed first.
      invokeLater { updateSelectedTabs() }
    }
  }

  /** Returns [StreamingDeviceId] of the selected tabs in the Running Devices Tool Window. */
  private fun getRunningDevicesSelectedTabs(): List<StreamingDeviceId> {
    val selectedContent = getAllContents().filter { it.isSelected }
    return selectedContent.mapNotNull { it.streamingDeviceId }
  }

  /** Returns the list of [StreamingDeviceId]s for every tab in the Running Devices Tool Window. */
  private fun getAllTabsDeviceIds(): List<StreamingDeviceId> {
    val contents = getAllContents()
    val tabIds =
      contents
        .map { it.component }
        .filterIsInstance<UiDataProvider>()
        .mapNotNull { dataProvider ->
          val dataContext = DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, dataProvider)
          STREAMING_DEVICE_ID_KEY.getData(dataContext)
        }

    return tabIds
  }

  /** Returns true if Running Devices has a tab containing a device with the desired serial number. */
  private fun hasDeviceWithSerialNumber(desiredSerialNumber: String): Boolean {
    val devicesIds = getAllTabsDeviceIds()
    return devicesIds.map { it.serialNumber }.contains(desiredSerialNumber)
  }

  /** Returns true if Running Devices has a tab containing a device associated with [streamingDeviceId]. */
  fun hasDevice(streamingDeviceId: StreamingDeviceId): Boolean {
    return hasDeviceWithSerialNumber(streamingDeviceId.serialNumber)
  }
}

private val Content.streamingDeviceId: StreamingDeviceId?
  get() {
    if (component !is UiDataProvider) {
      return null
    }
    val dataContext = DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, component)
    return STREAMING_DEVICE_ID_KEY.getData(dataContext)
  }
