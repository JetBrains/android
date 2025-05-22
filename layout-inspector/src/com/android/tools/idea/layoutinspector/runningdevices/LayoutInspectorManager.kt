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
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.runningdevices.ui.SelectedTabState
import com.android.tools.idea.layoutinspector.runningdevices.ui.TabComponents
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.OnDeviceRendererModel
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.OnDeviceRendererPanel
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.RootPanelRenderer
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.StudioRendererPanel
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.STREAMING_CONTENT_PANEL_KEY
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.EdtNoGetDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.scale.JBUIScale

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

  /** Disable embedded Layout Inspector by removing the injected UI from all tabs */
  fun disable()
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
    }

  private val stateListeners = mutableListOf<LayoutInspectorManager.StateListener>()

  /**
   * The list of tabs currently open in Running Devices, with or without Layout Inspector enabled.
   */
  private var existingRunningDevicesTabs: List<DeviceId> = emptyList()

  init {
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
    val selectedDataContext =
      IdeUiService.getInstance()
        .createCustomizedDataContext(
          DataContext.EMPTY_CONTEXT,
          EdtNoGetDataProvider { sink ->
            DataSink.uiDataSnapshot(sink, selectedTabContent!!.component)
          },
        )

    val streamingContentPanel = selectedDataContext.getData(STREAMING_CONTENT_PANEL_KEY)!!
    val displayView = selectedDataContext.getData(DISPLAY_VIEW_KEY)!!

    checkNotNull(selectedTabContent)
    checkNotNull(streamingContentPanel)

    val tabComponents =
      TabComponents(
        disposable = selectedTabContent,
        tabContentPanel = streamingContentPanel,
        tabContentPanelContainer = streamingContentPanel.parent,
        displayView = displayView,
      )

    val layoutInspector = project.getLayoutInspector()
    val rendererPanel = createRendererPanel(tabComponents, layoutInspector)
    return SelectedTabState(project, deviceId, tabComponents, layoutInspector, rendererPanel)
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

  override fun disable() {
    selectedTab = null
    tabsWithLayoutInspector = emptySet()
  }

  private fun updateListeners(
    listenersToUpdate: List<LayoutInspectorManager.StateListener> = stateListeners
  ) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    listenersToUpdate.forEach { listener -> listener.onStateUpdate(tabsWithLayoutInspector) }
  }
}

/**
 * Utility function to get [LayoutInspector] from a [Project] Call this only when LayoutInspector
 * needs to be used, see [LayoutInspectorProjectService.getLayoutInspector].
 */
private fun Project.getLayoutInspector(): LayoutInspector {
  return LayoutInspectorProjectService.getInstance(this).getLayoutInspector()
}

private fun createRendererPanel(
  tabComponents: TabComponents,
  layoutInspector: LayoutInspector,
): RootPanelRenderer {
  return RootPanelRenderer(
    disposable = tabComponents,
    renderModel = layoutInspector.renderModel,
    onDeviceRendererProvider = { parentDisposable ->
      val viewInspector =
        (layoutInspector.currentClient as? AppInspectionInspectorClient)?.viewInspector
      if (viewInspector == null) {
        throw IllegalStateException(
          "Trying to initialize on-device rendering with a null view inspector."
        )
      }
      OnDeviceRendererPanel(
        disposable = parentDisposable,
        scope = layoutInspector.coroutineScope,
        client = viewInspector.onDeviceRendering,
        renderModel =
          OnDeviceRendererModel(
            parentDisposable = parentDisposable,
            inspectorModel = layoutInspector.inspectorModel,
            treeSettings = layoutInspector.treeSettings,
            renderSettings = layoutInspector.renderSettings,
          ),
        enableSendRightClicksToDevice = { enable ->
          tabComponents.displayView.rightClicksAreSentToDevice = enable
        },
      )
    },
    studioRendererProvider = { parentDisposable ->
      StudioRendererPanel(
        disposable = parentDisposable,
        coroutineScope = layoutInspector.coroutineScope,
        renderLogic = layoutInspector.renderLogic,
        renderModel = layoutInspector.renderModel,
        notificationModel = layoutInspector.notificationModel,
        displayRectangleProvider = { tabComponents.displayView.displayRectangle },
        screenScaleProvider = { tabComponents.displayView.screenScalingFactor },
        orientationQuadrantProvider = {
          calculateRotationCorrection(
            layoutInspector.inspectorModel,
            displayOrientationQuadrant = { tabComponents.displayView.displayOrientationQuadrants },
            displayOrientationQuadrantCorrection = {
              tabComponents.displayView.displayOrientationCorrectionQuadrants
            },
          )
        },
        currentSessionStatistics = { layoutInspector.currentClient.stats },
      )
    },
  )
}

/**
 * Returns the quadrant in which the rendering of Layout Inspector should be rotated in order to
 * match the rendering from Running Devices. It does this by calculating the rotation difference
 * between the rotation of the device and the rotation of the rendering from Running Devices.
 *
 * Both the rendering from RD and the device can be rotated in all 4 quadrants, independently of
 * each other. We use the diff to reconcile the difference in rotation, as ultimately the rendering
 * from LI should match the rendering of the display from RD.
 *
 * Note that the rendering from Layout Inspector should be rotated only sometimes, to match the
 * rendering from Running Devices. Here are a few examples:
 * * Device is in portrait mode, auto-rotation is off, running devices rendering has no rotation ->
 *   apply no rotation
 * * Device is in landscape mode, auto-rotation is off, running devices rendering has rotation to be
 *   horizontal -> apply rotation, because the app is in portrait mode in the device, so should be
 *   rotated to match rendering from RD.
 * * Device is in landscape mode, auto-rotation is on, running devices rendering has rotation to be
 *   horizontal -> apply no rotation, because the app is already in landscape mode, so no rotation
 *   is needed to match rendering from RD.
 *
 * Note that: when rendering a streamed device (as opposed to an emulator), the Running Devices Tool
 * Window fakes the rotation of the screen (b/273699961). This means that for those cases we can't
 * reliably use the rotation provided by the device to calculate the rotation for the Layout
 * Inspector rendering. In these cases we should use the rotation correction provided by the RD Tool
 * Window. But in the case of emulators, the rotation correction from Running Devices is always 0.
 * In these case we should calculate our own rotation correction.
 */
@VisibleForTesting
fun calculateRotationCorrection(
  layoutInspectorModel: InspectorModel,
  displayOrientationQuadrant: () -> Int,
  displayOrientationQuadrantCorrection: () -> Int,
): Int {
  val orientationCorrectionFromRunningDevices = displayOrientationQuadrantCorrection()

  // Correction can be different from 0 only for streamed devices (as opposed to emulators).
  if (orientationCorrectionFromRunningDevices != 0) {
    return -orientationCorrectionFromRunningDevices
  }

  // The rotation of the display rendering coming from Running Devices.
  val displayRectangleOrientationQuadrant = displayOrientationQuadrant()

  // The rotation of the display coming from Layout Inspector.
  val layoutInspectorDisplayOrientationQuadrant =
    when (layoutInspectorModel.resourceLookup.displayOrientation) {
      0 -> 0
      90 -> 1
      180 -> 2
      270 -> 3
      else -> 0
    }

  // The difference in quadrant rotation between Layout Inspector rendering and the Running Devices
  // rendering.
  return (layoutInspectorDisplayOrientationQuadrant - displayRectangleOrientationQuadrant).mod(4)
}
