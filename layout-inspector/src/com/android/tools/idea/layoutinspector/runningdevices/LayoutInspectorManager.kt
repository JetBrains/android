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
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.android.tools.idea.layoutinspector.model.StatusNotificationAction
import com.android.tools.idea.layoutinspector.runningdevices.ui.SelectedTabState
import com.android.tools.idea.layoutinspector.runningdevices.ui.TabComponents
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorConfigurable
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.STREAMING_CONTENT_PANEL_KEY
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.EditorNotificationPanel.Status
import com.intellij.ui.scale.JBUIScale
import javax.swing.JComponent

const val SHOW_EMBEDDED_LI_BANNER_KEY =
  "com.android.tools.idea.layoutinspector.runningdevices.notification.show"
const val EMBEDDED_LI_MESSAGE_KEY = "embedded.inspector.notification.message"

const val SPLITTER_KEY =
  "com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManager.Splitter"

private const val DEFAULT_WINDOW_WIDTH = 800

/**
 * Object used to track tabs that have Layout Inspector enabled across multiple projects. Layout
 * Inspector should be enabled only once for each tab, across projects. Multiple projects connecting
 * to the same process is not a supported use case by Layout Inspector.
 */
object LayoutInspectorManagerGlobalState {
  val tabsWithLayoutInspector = mutableSetOf<DeviceId>()
}

/** Responsible for managing Layout Inspector in Running Devices Tool Window. */
interface LayoutInspectorManager : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LayoutInspectorManager {
      return project.getService(LayoutInspectorManager::class.java)
    }
  }

  fun interface StateListener {
    /**
     * Called each time the state of [LayoutInspectorManager] changes. Which happens each time
     * Layout Inspector is enabled or disabled for a tab.
     */
    fun onStateUpdate(state: Set<DeviceId>)
  }

  fun addStateListener(listener: StateListener)

  /** Injects or removes Layout Inspector in the tab associated to [deviceId]. */
  fun enableLayoutInspector(deviceId: DeviceId, enable: Boolean)

  /** Returns true if Layout Inspector is enabled for [deviceId], false otherwise. */
  fun isEnabled(deviceId: DeviceId): Boolean

  /** Returns true if Layout Inspector can be enabled for [deviceId], false otherwise. */
  fun isSupported(deviceId: DeviceId): Boolean
}

/** This class is meant to be used on the UI thread, to avoid concurrency issues. */
@UiThread
private class LayoutInspectorManagerImpl(private val project: Project) : LayoutInspectorManager {

  /** Tabs on which Layout Inspector is enabled. */
  private var tabsWithLayoutInspector = setOf<DeviceId>()
    set(value) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (value == field) {
        return
      }

      val tabsAdded = value - field
      val tabsRemoved = field - value

      // check if the selected tab was removed
      if (tabsRemoved.contains(selectedTab?.deviceId)) {
        selectedTab = null
      }

      field = value

      LayoutInspectorManagerGlobalState.tabsWithLayoutInspector.addAll(tabsAdded)
      LayoutInspectorManagerGlobalState.tabsWithLayoutInspector.removeAll(tabsRemoved)

      updateListeners()
    }

  /** The tab on which Layout Inspector is running */
  private var selectedTab: SelectedTabState? = null
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
      value.layoutInspector.foregroundProcessDetection?.start()

      val selectedDevice =
        value.layoutInspector.deviceModel?.devices?.find {
          it.serial == value.deviceId.serialNumber
        }
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

      showOptOutBanner(value.layoutInspector)
    }

  private val stateListeners = mutableListOf<LayoutInspectorManager.StateListener>()

  /**
   * The list of tabs currently open in Running Devices, with or without Layout Inspector enabled.
   */
  private var existingRunningDevicesTabs: List<DeviceId> = emptyList()

  init {
    check(LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled) {
      "LayoutInspectorManager is intended for use only in embedded Layout Inspector."
    }

    RunningDevicesStateObserver.getInstance(project)
      .addListener(
        object : RunningDevicesStateObserver.Listener {
          override fun onVisibleTabsChanged(visibleTabs: List<DeviceId>) {
            val visibleTabsWithLayoutInspector =
              visibleTabs.filter {
                // Keep only tabs that have layout inspector enabled on them.
                tabsWithLayoutInspector.contains(it)
              }

            if (visibleTabsWithLayoutInspector.size > 1) {
              // If there is more than one visible tab with Layout Inspector, remove Layout
              // Inspector from all tabs except for the current selected tab.
              // This can happen if multiple tabs have Layout Inspector enabled and the user splits
              // them into separate tool windows.
              // We don't want multiple selected tabs with Layout Inspector enabled because we
              // support running only one instance of Layout Inspector at a time.
              tabsWithLayoutInspector = selectedTab?.deviceId?.let { setOf(it) } ?: emptySet()
              return
            }

            val newSelectedTab = visibleTabsWithLayoutInspector.firstOrNull()

            if (newSelectedTab == selectedTab?.deviceId) {
              // The new selected tab is the same as the currently selected tab.
              return
            }

            selectedTab =
              if (newSelectedTab != null) {
                createTabState(newSelectedTab)
              } else {
                null
              }
          }

          override fun onExistingTabsChanged(existingTabs: List<DeviceId>) {
            existingRunningDevicesTabs = existingTabs
            // If the Running Devices Tool Window is collapsed, all tabs are removed.
            // We don't want to update our state when this happens, because it means we would lose
            // track of which tabs had Layout Inspector.
            // So instead we keep the tab state forever.
            // So if an emulator is disconnected with Layout Inspector turned on and later
            // restarted, Layout Inspector will be on again.
          }
        }
      )
  }

  private fun createTabState(deviceId: DeviceId): SelectedTabState {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val selectedTabContent =
      RunningDevicesStateObserver.getInstance(project).getTabContent(deviceId)
    val selectedTabDataProvider = selectedTabContent?.component as? DataProvider

    val streamingContentPanel =
      selectedTabDataProvider?.getData(STREAMING_CONTENT_PANEL_KEY.name) as? JComponent
    val displayView =
      selectedTabDataProvider?.getData(DISPLAY_VIEW_KEY.name) as? AbstractDisplayView

    checkNotNull(selectedTabContent)
    checkNotNull(streamingContentPanel)
    checkNotNull(displayView)

    val tabComponents =
      TabComponents(
        disposable = selectedTabContent,
        tabContentPanel = streamingContentPanel,
        tabContentPanelContainer = streamingContentPanel.parent,
        displayView = displayView,
      )

    return SelectedTabState(project, deviceId, tabComponents, project.getLayoutInspector())
  }

  override fun addStateListener(listener: LayoutInspectorManager.StateListener) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    updateListeners(listOf(listener))
    stateListeners.add(listener)
  }

  override fun enableLayoutInspector(deviceId: DeviceId, enable: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (enable) {
      val toolWindow =
        ToolWindowManager.getInstance(project).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID)
          as? ToolWindowEx
      toolWindow?.let {
        // When Running Devices tabs are in split mode, there can be multiple components.
        val currentWidth =
          toolWindow.contentManager.contentsRecursively.maxOfOrNull { it.component.width }
        val desiredWidth = JBUIScale.scale(DEFAULT_WINDOW_WIDTH)
        // Resize only if the tool window is currently smaller than the desired width.
        if (currentWidth != null && currentWidth < desiredWidth) {
          // Resize the tool window width, to be equal to DEFAULT_WINDOW_WIDTH.
          // stretchWidth resizes relatively to the current width of the tool window.
          toolWindow.stretchWidth(desiredWidth - currentWidth)
        }
      }

      selectedTab?.let {
        // We are enabling Layout Inspector on a new tab, but there is already a tab with Layout
        // Inspector enabled.
        // Layout Inspector does not support concurrent sessions, so we disable it in the previous
        // tab, before enabling in the new tab.
        // This can happen if Running Devices is running in split mode and multiple tabs are
        // visible at the same time.
        tabsWithLayoutInspector -= it.deviceId
      }

      if (tabsWithLayoutInspector.contains(deviceId)) {
        // do nothing if Layout Inspector is already enabled
        return
      }

      tabsWithLayoutInspector = tabsWithLayoutInspector + deviceId
      selectedTab = createTabState(deviceId)
    } else {
      if (!tabsWithLayoutInspector.contains(deviceId)) {
        // do nothing if Layout Inspector is not enabled
        return
      }

      tabsWithLayoutInspector = tabsWithLayoutInspector - deviceId
      if (selectedTab?.deviceId == deviceId) {
        selectedTab = null
      }
    }
  }

  override fun isEnabled(deviceId: DeviceId): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return selectedTab?.deviceId == deviceId
  }

  override fun isSupported(deviceId: DeviceId): Boolean {
    return RunningDevicesStateObserver.getInstance(project).hasDevice(deviceId)
  }

  override fun dispose() {
    selectedTab = null
    tabsWithLayoutInspector = emptySet()
  }

  private fun updateListeners(
    listenersToUpdate: List<LayoutInspectorManager.StateListener> = stateListeners
  ) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    listenersToUpdate.forEach { listener -> listener.onStateUpdate(tabsWithLayoutInspector) }
  }

  private fun showOptOutBanner(layoutInspector: LayoutInspector) {
    val notificationModel = layoutInspector.notificationModel
    val defaultValue = true
    val shouldShowWarning = {
      PropertiesComponent.getInstance().getBoolean(SHOW_EMBEDDED_LI_BANNER_KEY, defaultValue)
    }
    val setValue: (Boolean) -> Unit = {
      PropertiesComponent.getInstance().setValue(SHOW_EMBEDDED_LI_BANNER_KEY, it, defaultValue)
    }

    if (shouldShowWarning()) {
      notificationModel.addNotification(
        id = EMBEDDED_LI_MESSAGE_KEY,
        text = LayoutInspectorBundle.message(EMBEDDED_LI_MESSAGE_KEY),
        status = Status.Info,
        sticky = true,
        actions =
          listOf(
            StatusNotificationAction(LayoutInspectorBundle.message("do.not.show.again")) {
              notification ->
              setValue(false)
              notificationModel.removeNotification(notification.id)
            },
            StatusNotificationAction(LayoutInspectorBundle.message("opt.out")) {
              ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, LayoutInspectorConfigurable::class.java)
            },
          ),
      )
    } else {
      notificationModel.removeNotification(EMBEDDED_LI_MESSAGE_KEY)
    }
  }
}

/**
 * Utility function to get [LayoutInspector] from a [Project] Call this only when LayoutInspector
 * needs to be used, see [LayoutInspectorProjectService.getLayoutInspector].
 */
private fun Project.getLayoutInspector(): LayoutInspector {
  return LayoutInspectorProjectService.getInstance(this).getLayoutInspector()
}
