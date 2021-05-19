/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.DeviceViewSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.ui.enableLiveLayoutInspector
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.ide.DataManager
import com.intellij.ide.startup.ServiceNotReadyException
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.awt.BorderLayout
import javax.swing.JPanel

const val LAYOUT_INSPECTOR_TOOL_WINDOW_ID = "Layout Inspector"

private val LAYOUT_INSPECTOR = Key.create<LayoutInspector>("LayoutInspector")

val LAYOUT_INSPECTOR_DATA_KEY = DataKey.create<LayoutInspector>(LayoutInspector::class.java.name)

/**
 * Get the [LayoutInspector] for the specified layout inspector [toolWindow].
 */
fun lookupLayoutInspector(toolWindow: ToolWindow): LayoutInspector? =
  toolWindow.contentManager.getContent(0)?.getUserData(LAYOUT_INSPECTOR)

/**
 * Create a [DataProvider] for the specified [layoutInspector].
 */
@VisibleForTesting
fun dataProviderForLayoutInspector(layoutInspector: LayoutInspector): DataProvider =
  DataProvider { dataId -> if (LAYOUT_INSPECTOR_DATA_KEY.`is`(dataId)) layoutInspector else null }

/**
 * ToolWindowFactory: For creating a layout inspector tool window for the project.
 */
internal class LayoutInspectorToolWindowFactory : ToolWindowFactory {

  override fun isApplicable(project: Project): Boolean = enableLiveLayoutInspector

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    // Ensure the transport service is started
    if (TransportService.getInstance() == null) {
      throw ServiceNotReadyException()
    }
    val contentManager = toolWindow.contentManager

    val model = InspectorModel(project)
    val workbench = WorkBench<LayoutInspector>(project, LAYOUT_INSPECTOR_TOOL_WINDOW_ID, null, project)
    val viewSettings = DeviceViewSettings()
    val layoutInspector = LayoutInspector(model, workbench)
    val deviceViewPanel = DeviceViewPanel(layoutInspector, viewSettings, workbench)
    workbench.init(deviceViewPanel, layoutInspector, listOf(
      LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition()), false)

    val contentPanel = JPanel(BorderLayout())
    contentPanel.add(InspectorBanner(project), BorderLayout.NORTH)
    contentPanel.add(workbench, BorderLayout.CENTER)
    val content = contentManager.factory.createContent(contentPanel, "", true)
    content.putUserData(LAYOUT_INSPECTOR, layoutInspector)
    DataManager.registerDataProvider(workbench, dataProviderForLayoutInspector(layoutInspector))
    contentManager.addContent(content)
    project.messageBus.connect(workbench).subscribe(ToolWindowManagerListener.TOPIC, LayoutInspectorToolWindowManagerListener(project))
  }

  override fun shouldBeAvailable(project: Project): Boolean = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLED.get()
}

/**
 * Listen to state changes for the create layout inspector tool window.
 *
 * When the layout inspector is made visible (from a non visible state) attempt to auto connect.
 */
private class LayoutInspectorToolWindowManagerListener(private val project: Project) : ToolWindowManagerListener {
  private var wasWindowVisible = false

  override fun stateChanged() {
    val window = ToolWindowManager.getInstance(project).getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID) ?: return
    val isWindowVisible = window.isVisible // Layout Inspector tool window is expanded.
    val windowVisibilityChanged = isWindowVisible != wasWindowVisible
    wasWindowVisible = isWindowVisible
    if (windowVisibilityChanged && isWindowVisible) {
      UsageTracker.log(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT)
                         .setDynamicLayoutInspectorEvent(DynamicLayoutInspectorEvent.newBuilder()
                                                           .setType(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.OPEN))
                         .withProjectId(project))
    }
    val preferredProcess = getPreferredInspectorProcess(project) ?: return
    if (!windowVisibilityChanged || !isWindowVisible) {
      return
    }
    val inspector = lookupLayoutInspector(window) ?: return
    if (!inspector.currentClient.isConnected) {
      inspector.allClients.find { it.attachIfSupported(preferredProcess) != null }
    }
  }
}
