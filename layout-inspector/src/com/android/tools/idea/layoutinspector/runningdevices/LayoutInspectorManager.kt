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
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.android.tools.idea.layoutinspector.dataProviderForLayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.StatusNotificationAction
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorConfigurable
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.TargetSelectionActionFactory
import com.android.tools.idea.layoutinspector.ui.toolbar.createLayoutInspectorMainToolbar
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DEVICE_ID_KEY
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.STREAMING_CONTENT_PANEL_KEY
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorNotificationPanel.Status
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Container
import javax.swing.JComponent

private const val WORKBENCH_NAME = "Layout Inspector"

const val SHOW_EXPERIMENTAL_WARNING_KEY =
  "com.android.tools.idea.layoutinspector.runningdevices.experimental.notification.show"
const val EMBEDDED_EXPERIMENTAL_MESSAGE_KEY = "embedded.inspector.experimental.notification.message"

const val SPLITTER_KEY =
  "com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManager.Splitter"

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
      }

      field = value

      if (value == null) {
        return
      }

      // lock device model to only allow connections to this device
      value.layoutInspector.deviceModel?.forcedDeviceSerialNumber = value.deviceId.serialNumber

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
          previousTab?.deviceId in existingRunningDevicesTabs
        )
      }

      // inject Layout Inspector UI
      value.enableLayoutInspector()

      showExperimentalWarning(value.layoutInspector)
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
          override fun onSelectedTabChanged(deviceId: DeviceId?) {
            selectedTab =
              if (deviceId != null && tabsWithLayoutInspector.contains(deviceId)) {
                // Layout Inspector was enabled for this tab.
                createTabState(deviceId)
              } else {
                // Layout Inspector was not enabled for this tab.
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
    val runningDevicesContentManager = project.getRunningDevicesContentManager()
    val selectedTabContent =
      runningDevicesContentManager?.contents?.find { it.deviceId == deviceId }
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
        displayView = displayView
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
    return tabsWithLayoutInspector.contains(deviceId)
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

  /**
   * Class grouping components from a Running Devices tab. Used to inject Layout Inspector in the
   * tab. These components are disposed as soon as the tab is not visible or is not the main
   * selected tab. For this reason they should not be kept around if they don't belong to the
   * selected tab.
   *
   * @param tabContentPanel The component containing the main content of the tab (the display).
   * @param tabContentPanelContainer The container of [tabContentPanel].
   * @param displayView The [AbstractDisplayView] from running devices. Component on which the
   *   device display is rendered.
   */
  class TabComponents(
    disposable: Disposable,
    val tabContentPanel: JComponent,
    val tabContentPanelContainer: Container,
    val displayView: AbstractDisplayView
  ) : Disposable {
    init {
      Disposer.register(disposable, this)
    }

    override fun dispose() {}
  }

  /**
   * Represents the state of the selected tab.
   *
   * @param deviceId The id of selected tab.
   * @param tabComponents The components of the selected tab.
   * @param wrapLogic The logic used to wrap the tab in a workbench.
   */
  @UiThread
  private data class SelectedTabState(
    val project: Project,
    val deviceId: DeviceId,
    val tabComponents: TabComponents,
    val layoutInspector: LayoutInspector,
    val wrapLogic: WrapLogic =
      WrapLogic(tabComponents.tabContentPanel, tabComponents.tabContentPanelContainer),
    val layoutInspectorRenderer: LayoutInspectorRenderer =
      LayoutInspectorRenderer(
        tabComponents,
        layoutInspector.coroutineScope,
        layoutInspector.renderLogic,
        layoutInspector.renderModel,
        layoutInspector.notificationModel,
        { tabComponents.displayView.displayRectangle },
        { tabComponents.displayView.screenScalingFactor },
        {
          calculateRotationCorrection(
            layoutInspector.inspectorModel,
            displayOrientationQuadrant = { tabComponents.displayView.displayOrientationQuadrants },
            displayOrientationQuadrantCorrection = {
              tabComponents.displayView.displayOrientationCorrectionQuadrants
            }
          )
        },
        { layoutInspector.currentClient.stats },
      )
  ) : Disposable {

    /**
     * Disposable created each time the UI is injected, and disposed each time the UI is removed.
     * It's used to keep track of the lifecycle of the UI.
     */
    private var uiDisposable: Disposable? = null

    init {
      Disposer.register(tabComponents, this)
    }

    fun enableLayoutInspector() {
      ApplicationManager.getApplication().assertIsDispatchThread()

      val disposable = Disposer.newDisposable(tabComponents)
      uiDisposable = disposable

      wrapLogic.wrapComponent { centerPanel ->
        val inspectorPanel = BorderLayoutPanel()
        val toolsPanel = BorderLayoutPanel()

        val toggleDeepInspectAction =
          ToggleDeepInspectAction(
            { layoutInspectorRenderer.interceptClicks },
            { layoutInspectorRenderer.interceptClicks = it },
            { layoutInspector.currentClient }
          )

        val processPicker =
          TargetSelectionActionFactory.getSingleDeviceProcessPicker(
            layoutInspector,
            deviceId.serialNumber
          )
        val toolbar =
          createLayoutInspectorMainToolbar(
            toolsPanel,
            layoutInspector,
            processPicker,
            listOf(toggleDeepInspectAction)
          )

        val workBench = createLayoutInspectorWorkbench(project, disposable, layoutInspector)
        workBench.isFocusCycleRoot = false

        toolsPanel.add(toolbar.component, BorderLayout.NORTH)
        toolsPanel.add(workBench, BorderLayout.CENTER)
        workBench.component.border = JBUI.Borders.customLineTop(JBColor.border())

        val splitPanel =
          OnePixelSplitter(true, SPLITTER_KEY, 0.65f).apply {
            firstComponent = centerPanel
            secondComponent = toolsPanel
            setBlindZone { JBUI.insets(0, 1) }
          }

        val inspectorBanner = InspectorBanner(disposable, layoutInspector.notificationModel)
        inspectorPanel.add(inspectorBanner, BorderLayout.NORTH)
        inspectorPanel.add(splitPanel, BorderLayout.CENTER)
        inspectorPanel
      }
      tabComponents.displayView.add(layoutInspectorRenderer)

      layoutInspector.inspectorModel.selectionListeners.add(selectionChangedListener)
      layoutInspector.processModel?.addSelectedProcessListeners(
        EdtExecutorService.getInstance(),
        selectedProcessListener
      )
    }

    override fun dispose() {
      disableLayoutInspector()
    }

    private fun disableLayoutInspector() {
      ApplicationManager.getApplication().assertIsDispatchThread()

      uiDisposable?.let { Disposer.dispose(it) }
      uiDisposable = null

      wrapLogic.unwrapComponent()
      tabComponents.displayView.remove(layoutInspectorRenderer)
      layoutInspector.inspectorModel.selectionListeners.remove(selectionChangedListener)
      layoutInspector.processModel?.removeSelectedProcessListener(selectedProcessListener)

      tabComponents.tabContentPanelContainer.revalidate()
      tabComponents.tabContentPanelContainer.repaint()
    }

    private val selectionChangedListener:
      (old: ViewNode?, new: ViewNode?, origin: SelectionOrigin) -> Unit =
      { _, _, _ ->
        layoutInspectorRenderer.refresh()
      }

    private val selectedProcessListener = {
      // Sometimes on project close "SelectedTabContent#dispose" can be called after the listeners
      // are invoked.
      if (!project.isDisposed) {
        layoutInspector.inspectorClientSettings.isCapturingModeOn = true
        layoutInspectorRenderer.interceptClicks = false
      }
    }
  }

  private fun showExperimentalWarning(layoutInspector: LayoutInspector) {
    val notificationModel = layoutInspector.notificationModel
    val defaultValue = true
    val shouldShowWarning = {
      PropertiesComponent.getInstance().getBoolean(SHOW_EXPERIMENTAL_WARNING_KEY, defaultValue)
    }
    val setValue: (Boolean) -> Unit = {
      PropertiesComponent.getInstance().setValue(SHOW_EXPERIMENTAL_WARNING_KEY, it, defaultValue)
    }

    if (shouldShowWarning()) {
      notificationModel.addNotification(
        id = EMBEDDED_EXPERIMENTAL_MESSAGE_KEY,
        text = LayoutInspectorBundle.message(EMBEDDED_EXPERIMENTAL_MESSAGE_KEY),
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
          )
      )
    } else {
      notificationModel.removeNotification(EMBEDDED_EXPERIMENTAL_MESSAGE_KEY)
    }
  }
}

private fun createLayoutInspectorWorkbench(
  project: Project,
  parentDisposable: Disposable,
  layoutInspector: LayoutInspector,
): WorkBench<LayoutInspector> {
  ApplicationManager.getApplication().assertIsDispatchThread()
  val workbench = WorkBench<LayoutInspector>(project, WORKBENCH_NAME, null, parentDisposable)
  val toolsDefinition =
    listOf(LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition())
  workbench.init(layoutInspector, toolsDefinition, false)
  DataManager.registerDataProvider(workbench, dataProviderForLayoutInspector(layoutInspector))

  Disposer.register(parentDisposable) { DataManager.removeDataProvider(workbench) }

  return workbench
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
  displayOrientationQuadrantCorrection: () -> Int
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

/**
 * Utility function to get [LayoutInspector] from a [Project] Call this only when LayoutInspector
 * needs to be used, see [LayoutInspectorProjectService.getLayoutInspector].
 */
private fun Project.getLayoutInspector(): LayoutInspector {
  return LayoutInspectorProjectService.getInstance(this).getLayoutInspector()
}

private val Content.deviceId: DeviceId?
  get() {
    return (component as? DataProvider)?.getData(DEVICE_ID_KEY.name) as? DeviceId ?: return null
  }
