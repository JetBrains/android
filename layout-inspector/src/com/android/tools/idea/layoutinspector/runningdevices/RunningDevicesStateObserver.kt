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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.containers.DisposableWrapperList

/**
 * Class responsible for observing the state of Running Devices tabs. Can be used by other classes
 * as source for Running Devices state.
 */
@UiThread
class RunningDevicesStateObserver(project: Project) : Disposable {

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
      ApplicationManager.getApplication().assertIsDispatchThread()
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

  /**
   * Running devices supports split window mode, where multiple tabs are visible at the same time
   * and belong to multiple content managers. See b/325091329#comment8
   */
  private val contentManagers = DisposableWrapperList<ContentManager>()

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
    ApplicationManager.getApplication().assertIsDispatchThread()

    listener.onVisibleTabsChanged(visibleTabs)
    listener.onExistingTabsChanged(existingTabs)

    listeners.add(listener)
  }

  /**
   * Called to enable/disable the observer. Can be called periodically, for example from the update
   * method of AnAction.
   *
   * @param newContentManager The content manager associated with the update. For example, two
   *   actions added to two different tool windows will have a different content manager.
   */
  fun update(enabled: Boolean, newContentManager: ContentManager?) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (newContentManager == null) {
      return
    }

    if (enabled) {
      if (contentManagers.contains(newContentManager)) {
        // Content manager already registered.
        return
      }

      contentManagers.add(newContentManager, newContentManager)

      // Update the state with tabs from the new content manager.
      updateExistingTabs()
    } else {
      contentManagers.remove(newContentManager)
    }
  }

  fun getTabContent(deviceId: DeviceId): Content? {
    return contentManagers.flatMap { it.contents.toList() }.find { it.deviceId == deviceId }
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
    val selectedContent = contentManagers.mapNotNull { it.selectedContent }
    val selectedTabDataProvider = selectedContent.mapNotNull { it.component as? DataProvider }
    return selectedTabDataProvider.mapNotNull { it.getData(DEVICE_ID_KEY.name) as? DeviceId }
  }

  /** Returns the list of [DeviceId]s for every tab in the Running Devices Tool Window. */
  private fun getAllTabsDeviceIds(): List<DeviceId> {
    val contents = contentManagers.flatMap { it.contents.toList() }
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
