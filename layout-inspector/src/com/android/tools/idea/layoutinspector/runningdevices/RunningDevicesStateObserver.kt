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
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

data class TabId(val deviceSerialNumber: String)

/**
 * Class responsible for observing the state of Running Devices tabs.
 * Can be used by other classes as source for Running Devices state.
 */
@UiThread
class RunningDevicesStateObserver(private val project: Project) {

  interface Listener {
    /** Called when the selected tab in Running Devices changes */
    fun onSelectedTabChanged(tabId: TabId?)
    /** Called when a tab is added or removed to Running Devices */
    fun onExistingTabsChanged(existingTabs: List<TabId>)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunningDevicesStateObserver {
      return project.getService(RunningDevicesStateObserver::class.java)
    }
  }

  private val listeners = mutableListOf<Listener>()

  private var selectedTab: TabId? = null
    set(value) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (value == field) {
        return
      }

      field = value
      listeners.forEach { it.onSelectedTabChanged(value) }
    }

  private var existingTabs = emptyList<TabId>()
    set(value) {
      if (value == field) {
        return
      }


      field = value
      listeners.forEach { it.onExistingTabsChanged(value) }
    }

  private var contentManagerListener: RunningDevicesContentManagerListener? = null

  fun addListener(listener: Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    listener.onSelectedTabChanged(selectedTab)
    listener.onExistingTabsChanged(existingTabs)

    listeners.add(listener)
  }

  /**
   * Called to enable/disable the observer. Can be called periodically, for example from the update method of AnAction.
   */
  fun update(enabled: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (enabled) {
      if (contentManagerListener != null) {
        // observer is already registered
        return
      }

      val contentManager = project.getRunningDevicesContentManager()
      checkNotNull(contentManager)

      updateSelectedTab()
      updateExistingTabs()

      val runningDevicesContentManagerListener = RunningDevicesContentManagerListener()
      contentManager.addContentManagerListener(runningDevicesContentManagerListener)
      contentManagerListener = runningDevicesContentManagerListener
    }
    else {
      if (contentManagerListener == null) {
        // listener is not registered, so we're not observing anything
        return
      }

      val contentManager = project.getRunningDevicesContentManager()
      checkNotNull(contentManager)

      contentManager.removeContentManagerListener(contentManagerListener!!)
      contentManagerListener = null
      selectedTab = null
    }
  }

  private fun updateSelectedTab() {
    val tabId = project.getRunningDevicesSelectedTabDeviceSerialNumber()
    selectedTab = tabId
  }

  private fun updateExistingTabs() {
    val tabIds = project.getRunningDevicesExistingTabsDeviceSerialNumber()
    existingTabs = tabIds
  }

  /** [ContentManagerListener] used to observe the content of the Running Devices Tool Window. */
  private inner class RunningDevicesContentManagerListener : ContentManagerListener {
    override fun contentAdded(event: ContentManagerEvent) {
      // listeners are executed in order, if listeners before this one launched calls using invokeLater, they should be executed first.
      invokeLater { updateExistingTabs() }
    }

    override fun contentRemoveQuery(event: ContentManagerEvent) {
      // listeners are executed in order, if listeners before this one launched calls using invokeLater, they should be executed first.
      invokeLater { updateExistingTabs() }
    }

    override fun selectionChanged(event: ContentManagerEvent) {
      // listeners are executed in order, if listeners before this one launched calls using invokeLater, they should be executed first.
      invokeLater { updateSelectedTab() }
    }
  }
}

fun Project.getRunningDevicesContentManager(): ContentManager? {
  return getServiceIfCreated(ToolWindowManager::class.java)
    ?.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID)
    ?.contentManager
}

/**
 * Returns [TabId] of the selected tab in the Running Devices Tool Window.
 */
private fun Project.getRunningDevicesSelectedTabDeviceSerialNumber(): TabId? {
  val contentManager = getRunningDevicesContentManager() ?: return null
  val selectedContent = contentManager.selectedContent ?: return null
  val selectedTabDataProvider = selectedContent.component as? DataProvider ?: return null

  val deviceSerialNumber =  selectedTabDataProvider.getData(SERIAL_NUMBER_KEY.name) as? String ?: return null
  return TabId(deviceSerialNumber)
}

/**
 * Returns the list of [TabId]s for every tab in the Running Devices Tool Window.
 */
private fun Project.getRunningDevicesExistingTabsDeviceSerialNumber(): List<TabId> {
  val contentManager = getRunningDevicesContentManager() ?: return emptyList()
  val contents = contentManager.contents ?: return emptyList()
  val tabIds = contents
    .map { it.component }
    .filterIsInstance<DataProvider>()
    .mapNotNull { dataProvider ->
      dataProvider.getData(SERIAL_NUMBER_KEY.name) as? String
    }
    .map { TabId(it) }

  return tabIds
}