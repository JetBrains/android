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
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.core.DEVICE_ID_KEY
import com.android.tools.idea.streaming.core.DeviceId
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

/**
 * Class responsible for observing the state of Running Devices tabs. Can be used by other classes
 * as source for Running Devices state.
 */
@UiThread
class RunningDevicesStateObserver(project: Project) : Disposable {

  interface Listener {
    /** Called when the selected tab in Running Devices changes */
    fun onSelectedTabChanged(deviceId: DeviceId?)
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

  private var selectedTab: DeviceId? = null
    set(value) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (value == field) {
        return
      }

      field = value
      listeners.forEach { it.onSelectedTabChanged(value) }
    }

  private var existingTabs = emptyList<DeviceId>()
    set(value) {
      if (value == field) {
        return
      }

      field = value
      listeners.forEach { it.onExistingTabsChanged(value) }
    }

  // TODO(b/324741151): Add support for multiple context managers.
  private var runningDevicesContext: RunningDevicesContext? = null

  init {
    // Listen for changes to RD Tool Window state.
    val messageBusConnection = project.messageBus.connect(this)
    messageBusConnection.subscribe(
      ToolWindowManagerListener.TOPIC,
      object : ToolWindowManagerListener {

        override fun stateChanged(toolWindowManager: ToolWindowManager) {
          val toolWindow = toolWindowManager.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return

          toolWindowManager.invokeLater {
            if (!toolWindow.isDisposed) {
              // Restore selected tabs in case they are removed when the tool window is hidden.
              updateSelectedTab()
            }
          }
        }
      },
    )
  }

  override fun dispose() {}

  fun addListener(listener: Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    listener.onSelectedTabChanged(selectedTab)
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

    if (enabled) {
      if (runningDevicesContext != null) {
        // Context already registered.
        return
      }

      checkNotNull(newContentManager)

      val runningDevicesContentManagerListener = RunningDevicesContentManagerListener()
      newContentManager.addContentManagerListener(runningDevicesContentManagerListener)

      runningDevicesContext =
        RunningDevicesContext(newContentManager, runningDevicesContentManagerListener)

      updateSelectedTab()
      updateExistingTabs()
    } else {
      // If context is null, we're not registered, so we're not observing anything.
      val localRunningDevicesContext = runningDevicesContext ?: return

      localRunningDevicesContext.contentManager.removeContentManagerListener(
        localRunningDevicesContext.contentManagerListener
      )

      runningDevicesContext = null
      selectedTab = null
    }
  }

  fun getTabContent(deviceId: DeviceId): Content? {
    return runningDevicesContext?.contentManager?.contents?.find { it.deviceId == deviceId }
  }

  private fun updateSelectedTab() {
    val deviceId = getSelectedTabDeviceId()
    selectedTab = deviceId
  }

  private fun updateExistingTabs() {
    val deviceIds = getAllTabsDeviceIds()
    existingTabs = deviceIds
  }

  private data class RunningDevicesContext(
    val contentManager: ContentManager,
    val contentManagerListener: RunningDevicesContentManagerListener,
  )

  /** [ContentManagerListener] used to observe the content of the Running Devices Tool Window. */
  private inner class RunningDevicesContentManagerListener : ContentManagerListener {
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
      invokeLater { updateSelectedTab() }
    }
  }

  /** Returns [DeviceId] of the selected tab in the Running Devices Tool Window. */
  private fun getSelectedTabDeviceId(): DeviceId? {
    val contentManager = runningDevicesContext?.contentManager ?: return null
    val selectedContent = contentManager.selectedContent ?: return null
    val selectedTabDataProvider = selectedContent.component as? DataProvider ?: return null

    return selectedTabDataProvider.getData(DEVICE_ID_KEY.name) as? DeviceId ?: return null
  }

  /** Returns the list of [DeviceId]s for every tab in the Running Devices Tool Window. */
  private fun getAllTabsDeviceIds(): List<DeviceId> {
    val contentManager = runningDevicesContext?.contentManager ?: return emptyList()
    val contents = contentManager.contents
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
  fun hasDeviceWithSerialNumber(desiredSerialNumber: String): Boolean {
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
