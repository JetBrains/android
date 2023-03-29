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
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.android.tools.idea.layoutinspector.dataProviderForLayoutInspector
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.streaming.AbstractDisplayView
import com.android.tools.idea.streaming.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.streaming.STREAMING_CONTENT_PANEL_KEY
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import java.awt.Container
import javax.swing.JComponent

private const val WORKBENCH_NAME = "Layout Inspector"

/** Responsible for managing Layout Inspector in Running Devices Tool Window. */
interface LayoutInspectorManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LayoutInspectorManager {
      return project.getService(LayoutInspectorManager::class.java)
    }
  }

  fun interface StateListener {
    /**
     * Called each time the state of [LayoutInspectorManager] changes.
     * Which happens each time Layout Inspector is enabled or disabled for a tab.
     */
    fun onStateUpdate(state: Set<TabId>)
  }

  fun addStateListener(listener: StateListener)

  /** Injects or removes Layout Inspector in the tab associated to [tabId]. */
  fun enableLayoutInspector(tabId: TabId, enable: Boolean)

  /** Returns true if Layout Inspector is enabled for [tabId], false otherwise. */
  fun isEnabled(tabId: TabId): Boolean
}

/** This class is meant to be used on the UI thread, to avoid concurrency issues. */
@UiThread
// TODO(b/265150325): do not use project as disposable
private class LayoutInspectorManagerImpl(private val project: Project) : LayoutInspectorManager {

  /** Tabs on which Layout Inspector is enabled. */
  private var tabsWithLayoutInspector = setOf<TabId>()
    set(value) {
      if (value == field) {
        return
      }

      // check if the selected tab was removed
      val removedTabs = tabsWithLayoutInspector - value
      if (removedTabs.contains(selectedTab?.tabId)) {
        selectedTab = null
      }

      field = value
      updateListeners()
    }

  /** The tab on which Layout Inspector is running */
  private var selectedTab: SelectedTabState? = null
    set(value) {
      if (field == value) {
        return
      }

      field?.disableLayoutInspector()
      field?.layoutInspector?.stopInspector()

      field = value

      // TODO(b/265150325): if the device just showed up or LayoutInspector was just started, deviceModel might not have the device yet
      //  In this case LayoutInspector will connect to the first device that shows up. This can produce race conditions if two devices
      //  are started at the same time.
      val selectedDevice = value?.layoutInspector?.deviceModel?.devices?.find { it.serial == value.tabId.deviceSerialNumber }
      if (selectedDevice != null) {
        // start polling
        value.layoutInspector.foregroundProcessDetection?.startPollingDevice(selectedDevice)
      }

      // inject Layout Inspector UI
      value?.enableLayoutInspector()
    }

  private val stateListeners = mutableListOf<LayoutInspectorManager.StateListener>()

  init {
    RunningDevicesStateObserver.getInstance(project).addListener(object : RunningDevicesStateObserver.Listener {
      override fun onSelectedTabChanged(tabId: TabId?) {
        selectedTab = if (tabId != null && tabsWithLayoutInspector.contains(tabId)) {
          // Layout Inspector was enabled for this tab.
          createTabState(tabId)
        }
        else {
          // Layout Inspector was not enabled for this tab.
          null
        }
      }

      override fun onExistingTabsChanged(existingTabs: List<TabId>) {
        // If the Running Devices Tool Window is collapsed, all tabs are removed.
        // We don't want to update our state when this happens, because it means we would lose track of which tabs had Layout Inspector.
        // So instead we keep the tab state forever.
        // So if an emulator is disconnected with Layout Inspector turned on and later restarted, Layout Inspector will be on again.
      }
    })
  }

  private fun createTabState(tabId: TabId): SelectedTabState {
    val runningDevicesContentManager = project.getRunningDevicesContentManager()
    val selectedTabContent = runningDevicesContentManager?.contents?.find { it.tabId == tabId }
    val selectedTabDataProvider = selectedTabContent?.component as? DataProvider

    val streamingContentPanel = selectedTabDataProvider?.getData(STREAMING_CONTENT_PANEL_KEY.name) as? JComponent
    val displayView = selectedTabDataProvider?.getData(DISPLAY_VIEW_KEY.name) as? AbstractDisplayView

    checkNotNull(selectedTabContent)
    checkNotNull(streamingContentPanel)
    checkNotNull(displayView)

    val tabComponents = TabComponents(
      disposable = selectedTabContent,
      tabContentPanel = streamingContentPanel,
      tabContentPanelContainer = streamingContentPanel.parent,
      displayView = displayView
    )

    return SelectedTabState(project, tabId, tabComponents, project.getLayoutInspector())
  }

  override fun addStateListener(listener: LayoutInspectorManager.StateListener) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    updateListeners(listOf(listener))
    stateListeners.add(listener)
  }

  override fun enableLayoutInspector(tabId: TabId, enable: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (enable) {
      if (tabsWithLayoutInspector.contains(tabId)) {
        // do nothing if Layout Inspector is already enabled
        return
      }

      tabsWithLayoutInspector = tabsWithLayoutInspector + tabId
      selectedTab = createTabState(tabId)
    }
    else {
      if (!tabsWithLayoutInspector.contains(tabId)) {
        // do nothing if Layout Inspector is not enabled
        return
      }

      tabsWithLayoutInspector = tabsWithLayoutInspector - tabId
      if (selectedTab?.tabId == tabId) {
        selectedTab = null
      }
    }
  }

  override fun isEnabled(tabId: TabId): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return tabsWithLayoutInspector.contains(tabId)
  }

  private fun updateListeners(listenersToUpdate: List<LayoutInspectorManager.StateListener> = stateListeners) {
    listenersToUpdate.forEach { listener -> listener.onStateUpdate(tabsWithLayoutInspector) }
  }

  /**
   * Class grouping components from a Running Devices tab. Used to inject Layout Inspector in the tab.
   * These components are disposed as soon as the tab is not visible or is not the main selected tab.
   * For this reason they should not be kept around if they don't belong to the selected tab.
   * @param tabContentPanel The component containing the main content of the tab (the display).
   * @param tabContentPanelContainer The container of [tabContentPanel].
   * @param displayView The [AbstractDisplayView] from running devices. Component on which the device display is rendered.
   */
  private class TabComponents(
    disposable: Disposable,
    val tabContentPanel: JComponent,
    val tabContentPanelContainer: Container,
    val displayView: AbstractDisplayView
  ): Disposable {
    init {
      Disposer.register(disposable, this)
    }
    override fun dispose() { }
  }

  /**
   * Represents the state of the selected tab.
   * @param tabId The id of selected tab.
   * @param tabComponents The components of the selected tab.
   * @param wrapLogic The logic used to wrap the tab in a workbench.
   * @param displayViewManager The component responsible for rendering Layout Inspector on the selected tab.
   */
  private data class SelectedTabState(
    val project: Project,
    val tabId: TabId,
    val tabComponents: TabComponents,
    val layoutInspector: LayoutInspector,
    val wrapLogic: WrapLogic = WrapLogic(tabComponents.tabContentPanel, tabComponents.tabContentPanelContainer),
    val displayViewManager: DisplayViewManager = DisplayViewManager(
      layoutInspector.renderModel,
      layoutInspector.renderLogic,
      tabComponents.displayView
    )
  ) {

    fun enableLayoutInspector() {
      wrapLogic.wrapComponent { centerPanel ->
        createLayoutInspectorWorkbench(project, layoutInspector, centerPanel)
      }
      displayViewManager.startRendering()
    }

    fun disableLayoutInspector() {
      wrapLogic.unwrapComponent()
      displayViewManager.stopRendering()
    }
  }
}

private fun createLayoutInspectorWorkbench(
  project: Project,
  layoutInspector: LayoutInspector,
  centerPanel: JComponent
): WorkBench<LayoutInspector> {
  val workbench = WorkBench<LayoutInspector>(project, WORKBENCH_NAME, null, project)
  val toolsDefinition = listOf(LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition())
  workbench.init(centerPanel, layoutInspector, toolsDefinition, false)
  DataManager.registerDataProvider(workbench, dataProviderForLayoutInspector(layoutInspector))
  return workbench
}

/** Utility function to get [LayoutInspector] from a [Project] */
private fun Project.getLayoutInspector(): LayoutInspector {
  return LayoutInspectorProjectService
    .getInstance(this)
    .getLayoutInspector()
}

private val Content.tabId: TabId?
  get() {
    val deviceSerialNumber = (component as? DataProvider)?.getData(SERIAL_NUMBER_KEY.name) as? String ?: return null
    return TabId(deviceSerialNumber)
  }