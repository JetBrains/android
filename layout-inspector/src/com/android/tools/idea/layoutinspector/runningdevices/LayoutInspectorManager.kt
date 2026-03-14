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
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.android.tools.idea.layoutinspector.runningdevices.ui.ActiveTabState
import com.android.tools.idea.layoutinspector.runningdevices.ui.createTabComponents
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.core.StreamingDeviceId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.scale.JBUIScale

const val SPLITTER_KEY = "com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManager.Splitter"

private const val DEFAULT_WINDOW_WIDTH = 800

/**
 * Object used to track tabs that have Layout Inspector enabled across multiple projects. Layout Inspector should be enabled only once for
 * each tab, across projects. Multiple projects connecting to the same process is not a supported use case by Layout Inspector.
 */
object LayoutInspectorManagerGlobalState {
  val tabsWithLayoutInspector = mutableSetOf<StreamingDeviceId>()
}

/** Responsible for managing Layout Inspector in Running Devices Tool Window. */
interface LayoutInspectorManager : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LayoutInspectorManager {
      return project.getService(LayoutInspectorManager::class.java)
    }
  }

  /** Injects or removes Layout Inspector in the tab associated to [streamingDeviceId]. */
  fun enableLayoutInspector(streamingDeviceId: StreamingDeviceId, enable: Boolean)

  /** Returns true if Layout Inspector is enabled for [streamingDeviceId], false otherwise. */
  fun isEnabled(streamingDeviceId: StreamingDeviceId): Boolean

  /** Returns true if Layout Inspector can be enabled for [streamingDeviceId], false otherwise. */
  fun isSupported(streamingDeviceId: StreamingDeviceId): Boolean

  /** Disable embedded Layout Inspector by removing the injected UI from all tabs */
  fun disable()
}

/** This class is meant to be used on the UI thread, to avoid concurrency issues. */
@UiThread
internal class LayoutInspectorManagerImpl(private val project: Project) : LayoutInspectorManager {

  /** Tabs on which Layout Inspector is enabled. */
  private var tabsWithLayoutInspector = setOf<StreamingDeviceId>()
    set(value) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (value == field) {
        return
      }

      val tabsAdded = value - field
      val tabsRemoved = field - value

      // check if the selected tab was removed
      if (tabsRemoved.contains(activeTab?.deviceId)) {
        activeTab = null
      }

      field = value

      LayoutInspectorManagerGlobalState.tabsWithLayoutInspector.addAll(tabsAdded)
      LayoutInspectorManagerGlobalState.tabsWithLayoutInspector.removeAll(tabsRemoved)
    }

  /**
   * The tab on which Layout Inspector is running. The tab might not be visible. Layout Inspector keeps running in this tab until the tab is
   * destroyed, or a new [activeTab] is set.
   */
  private var activeTab: ActiveTabState? = null
    set(value) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (field == value) {
        return
      }

      val previousTab = field
      if (previousTab != null) {
        // Dispose to trigger clean up.
        Disposer.dispose(previousTab.tabComponents)
        previousTab.layoutInspector.stopInspector()
        previousTab.layoutInspector.deviceModel?.forcedDeviceSerialNumber = null
        // Calling foregroundProcessDetection.start and stop from LayoutInspectorManager is a
        // workaround used to prevent foreground process detection from running in the background
        // even when embedded LI is not enabled on any device. This won't be necessary when we will
        // be able to create a new instance of LayoutInspector for each tab in Running Devices,
        // instead of having a single global instance of LayoutInspector shared by all the tabs. See
        // b/304540563
        previousTab.layoutInspector.foregroundProcessDetection?.stop()
      }

      field = value

      if (value == null) {
        return
      }

      // lock device model to only allow connections to this device
      value.layoutInspector.deviceModel?.forcedDeviceSerialNumber = value.deviceId.serialNumber
      value.layoutInspector.foregroundProcessDetection?.start(value.deviceId.serialNumber)

      val selectedDevice = value.layoutInspector.deviceModel?.devices?.find { it.serial == value.deviceId.serialNumber }
      // the device might not be available yet in app inspection
      if (selectedDevice != null) {
        // start polling
        value.layoutInspector.foregroundProcessDetection?.startPollingDevice(
          selectedDevice,
          // only stop polling if the previous tab is still open.
          previousTab?.deviceId in existingRunningDevicesTabs,
        )
      }

      // inject Layout Inspector UI
      value.enableLayoutInspector()
    }

  /** The list of tabs currently open in Running Devices, with or without Layout Inspector enabled. */
  private var existingRunningDevicesTabs: List<StreamingDeviceId> = emptyList()

  /** The tabs currently selected in running devices. There can be more than one when in split mode. */
  private var selectedRunningDevicesTabs: List<StreamingDeviceId> = emptyList()

  init {
    RunningDevicesStateObserver.getInstance(project)
      .addListener(
        object : RunningDevicesStateObserver.Listener {
          override fun onSelectedTabsChanged(selectedTabs: List<StreamingDeviceId>) {
            selectedRunningDevicesTabs = selectedTabs

            val selectedTabsWithLayoutInspector =
              selectedTabs.filter {
                // Keep only tabs that have layout inspector enabled on them.
                tabsWithLayoutInspector.contains(it)
              }

            if (selectedTabsWithLayoutInspector.size > 1) {
              // If there is more than one selected tab with Layout Inspector, remove Layout
              // Inspector from all tabs except for the current selected tab.
              // This can happen if multiple tabs have Layout Inspector enabled and the user splits
              // them into separate tool windows.
              // We don't want multiple selected tabs with Layout Inspector enabled because we
              // support running only one instance of Layout Inspector at a time.
              tabsWithLayoutInspector = activeTab?.deviceId?.let { setOf(it) } ?: emptySet()
            } else {
              val newSelectedTab = selectedTabsWithLayoutInspector.firstOrNull()

              if (newSelectedTab != null && newSelectedTab != activeTab?.deviceId) {
                // There is a new selected tab and the new selected tab is different from the old selected tab
                activeTab = createTabState(newSelectedTab)
              }
            }
          }

          override fun onExistingTabsChanged(existingTabs: List<StreamingDeviceId>) {
            existingRunningDevicesTabs = existingTabs
            if (activeTab != null && !existingTabs.contains(activeTab!!.deviceId)) {
              // The selected tab doesn't exist anymore, we set selectedTab to null to disconnect layout inspector and release resources.
              // We keep the tab in tabsWithLayoutInspector so that it can be restored when the tab returns.
              activeTab = null
            }
          }
        }
      )
  }

  private fun createTabState(streamingDeviceId: StreamingDeviceId): ActiveTabState {
    val tabComponents = createTabComponents(project, streamingDeviceId)

    val layoutInspector = project.getLayoutInspector()
    return ActiveTabState(
      disposable = tabComponents,
      project = project,
      deviceId = streamingDeviceId,
      tabComponents = tabComponents,
      layoutInspector = layoutInspector,
    )
  }

  override fun enableLayoutInspector(streamingDeviceId: StreamingDeviceId, enable: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (enable) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) as? ToolWindowEx
      toolWindow?.let {
        // When Running Devices tabs are in split mode, there can be multiple components.
        val currentWidth = toolWindow.contentManager.contentsRecursively.maxOfOrNull { it.component.width }
        val desiredWidth = JBUIScale.scale(DEFAULT_WINDOW_WIDTH)
        // Resize only if the tool window is currently smaller than the desired width.
        if (currentWidth != null && currentWidth < desiredWidth) {
          // Resize the tool window width, to be equal to DEFAULT_WINDOW_WIDTH.
          // stretchWidth resizes relatively to the current width of the tool window.
          toolWindow.stretchWidth(desiredWidth - currentWidth)
        }
      }

      activeTab?.let {
        if (selectedRunningDevicesTabs.contains(it.deviceId)) {
          // We are enabling Layout Inspector on a new tab, but there is already a tab with Layout Inspector enabled.
          // Layout Inspector does not support concurrent sessions, so we disable it in the previous tab, before enabling in the new tab.
          // This can happen if Running Devices is running in split mode and multiple tabs are visible at the same time.
          tabsWithLayoutInspector -= it.deviceId
        }
      }

      if (tabsWithLayoutInspector.contains(streamingDeviceId)) {
        // do nothing if Layout Inspector is already enabled
        return
      }

      tabsWithLayoutInspector = tabsWithLayoutInspector + streamingDeviceId
      activeTab = createTabState(streamingDeviceId)
    } else {
      if (!tabsWithLayoutInspector.contains(streamingDeviceId)) {
        // do nothing if Layout Inspector is not enabled
        return
      }

      tabsWithLayoutInspector = tabsWithLayoutInspector - streamingDeviceId
      if (activeTab?.deviceId == streamingDeviceId) {
        activeTab = null
      }
    }
  }

  override fun isEnabled(streamingDeviceId: StreamingDeviceId): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return activeTab?.deviceId == streamingDeviceId
  }

  override fun isSupported(streamingDeviceId: StreamingDeviceId): Boolean {
    return RunningDevicesStateObserver.getInstance(project).hasDevice(streamingDeviceId)
  }

  override fun dispose() {
    activeTab = null
    tabsWithLayoutInspector = emptySet()
  }

  override fun disable() {
    activeTab = null
    tabsWithLayoutInspector = emptySet()
  }
}

/**
 * Utility function to get [LayoutInspector] from a [Project] Call this only when LayoutInspector needs to be used, see
 * [LayoutInspectorProjectService.getLayoutInspector].
 */
private fun Project.getLayoutInspector(): LayoutInspector {
  return LayoutInspectorProjectService.getInstance(this).getLayoutInspector()
}
