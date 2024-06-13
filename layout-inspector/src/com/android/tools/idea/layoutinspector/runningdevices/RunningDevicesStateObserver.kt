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
import com.android.tools.idea.streaming.core.DEVICE_ID_KEY
import com.android.tools.idea.streaming.core.DeviceId
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Class responsible for observing the state of Running Devices tabs. Can be used by other classes
 * as source for Running Devices state.
 */
@UiThread
@Service(Service.Level.PROJECT)
class RunningDevicesStateObserver(
  private val project: Project,
  private val scope: CoroutineScope
) : Disposable {

  interface Listener {
    /**
     * Called when the visible tabs in Running Devices change. There can be more than one visible
     * tab if Running Deices is running in split window mode.
     */
    fun onVisibleTabsChanged(visibleTabs: List<DeviceId>)

    /** Called when a tab is added or removed to Running Devices */
    fun onExistingTabsChanged(existingTabs: List<DeviceId>)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunningDevicesStateObserver {
      return project.getService(RunningDevicesStateObserver::class.java)
    }
  }

  private val listeners = mutableListOf<Listener>()

  private var visibleTabs: List<DeviceId> = emptyList()
    set(value) {
      ThreadingAssertions.assertEventDispatchThread()
      if (value == field) {
        return
      }

      field = value
      listeners.forEach { it.onVisibleTabsChanged(value) }
    }

  private var existingTabs = emptyList<DeviceId>()
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
              RunningDevicesContentManagerListener(toolWindow).also {
                Disposer.register(this@RunningDevicesStateObserver, it)
              }
          }

          toolWindowManager.invokeLater {
            if (!toolWindow.isDisposed) {
              if (toolWindow.isVisible) {
                // Restore visible tabs that were removed when the tool window was hidden.
                updateVisibleTabs()
              } else {
                visibleTabs = emptyList()
              }
            }
          }
        }
      },
    )
  }

  override fun dispose() {}

  fun addListener(listener: Listener) {
    scope.launch(Dispatchers.Main.immediate) {
      listener.onVisibleTabsChanged(visibleTabs)
      listener.onExistingTabsChanged(existingTabs)

      listeners.add(listener)
    }
  }

  /** Returns a list of all content from Running Devices, across all existing ContentManagers. */
  private fun getAllContents(): List<Content> {
    val toolWindow =
      project
        .getServiceIfCreated(ToolWindowManager::class.java)
        ?.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return emptyList()
    return toolWindow.contentManagerIfCreated?.contentsRecursively ?: emptyList()
  }

  fun getTabContent(deviceId: DeviceId): Content? {
    return getAllContents().find { it.deviceId == deviceId }
  }

  private fun updateVisibleTabs() {
    val deviceIds = getRunningDevicesVisibleTabs()
    visibleTabs = deviceIds
  }

  private fun updateExistingTabs() {
    val deviceIds = getAllTabsDeviceIds()
    existingTabs = deviceIds
  }

  /** [ContentManagerListener] used to observe the content of the Running Devices Tool Window. */
  private inner class RunningDevicesContentManagerListener(toolWindow: ToolWindow) :
    ContentManagerHierarchyAdapter(toolWindow) {
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
      invokeLater { updateVisibleTabs() }
    }
  }

  /** Returns [DeviceId] of the visible tabs in the Running Devices Tool Window. */
  private fun getRunningDevicesVisibleTabs(): List<DeviceId> {
    val selectedContent = getAllContents().filter { it.isSelected }
    val selectedTabDataProvider = selectedContent.mapNotNull { it.component as? DataProvider }
    return selectedTabDataProvider.mapNotNull { it.getData(DEVICE_ID_KEY.name) as? DeviceId }
  }

  /** Returns the list of [DeviceId]s for every tab in the Running Devices Tool Window. */
  private fun getAllTabsDeviceIds(): List<DeviceId> {
    val contents = getAllContents()
    val tabIds =
      contents
        .map { it.component }
        .filterIsInstance<DataProvider>()
        .mapNotNull { dataProvider -> dataProvider.getData(DEVICE_ID_KEY.name) as? DeviceId }

    return tabIds
  }

  /**
   * Returns true if Running Devices has a tab containing a device with the desired serial number.
   */
  private fun hasDeviceWithSerialNumber(desiredSerialNumber: String): Boolean {
    val devicesIds = getAllTabsDeviceIds()
    return devicesIds.map { it.serialNumber }.contains(desiredSerialNumber)
  }

  /** Returns true if Running Devices has a tab containing a device associated with [deviceId]. */
  fun hasDevice(deviceId: DeviceId): Boolean {
    return hasDeviceWithSerialNumber(deviceId.serialNumber)
  }
}

private val Content.deviceId: DeviceId?
  get() {
    return (component as? DataProvider)?.getData(DEVICE_ID_KEY.name) as? DeviceId ?: return null
  }
