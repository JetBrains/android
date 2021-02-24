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
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.DeviceViewSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.ui.enableLiveLayoutInspector
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.ide.DataManager
import com.intellij.ide.startup.ServiceNotReadyException
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.concurrency.EdtExecutorService
import java.awt.BorderLayout
import javax.swing.JPanel

const val LAYOUT_INSPECTOR_TOOL_WINDOW_ID = "Layout Inspector"

val LAYOUT_INSPECTOR_DATA_KEY = DataKey.create<LayoutInspector>(LayoutInspector::class.java.name)

/**
 * Create a [DataProvider] for the specified [layoutInspector].
 */
@VisibleForTesting
fun dataProviderForLayoutInspector(layoutInspector: LayoutInspector): DataProvider =
  DataProvider { dataId -> if (LAYOUT_INSPECTOR_DATA_KEY.`is`(dataId)) layoutInspector else null }

/**
 * ToolWindowFactory: For creating a layout inspector tool window for the project.
 */
class LayoutInspectorToolWindowFactory : ToolWindowFactory {

  override fun isApplicable(project: Project): Boolean = enableLiveLayoutInspector

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    // Ensure the transport service is started
    if (TransportService.getInstance() == null) {
      throw ServiceNotReadyException()
    }
    val model = InspectorModel(project)
    val workbench = WorkBench<LayoutInspector>(project, LAYOUT_INSPECTOR_TOOL_WINDOW_ID, null, project)
    val viewSettings = DeviceViewSettings()

    val edtExecutor = EdtExecutorService.getInstance()

    val contentPanel = JPanel(BorderLayout())
    contentPanel.add(InspectorBanner(project), BorderLayout.NORTH)
    contentPanel.add(workbench, BorderLayout.CENTER)

    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(contentPanel, "", true)
    contentManager.addContent(content)

    workbench.showLoading("Initializing ADB")
    AndroidExecutors.getInstance().workerThreadExecutor.execute {
      val adb = AdbUtils.getAdbFuture(project).get()
      edtExecutor.execute {
        workbench.hideLoading()

        val processes = ProcessesModel(edtExecutor, AppInspectionDiscoveryService.instance.apiServices.processNotifier) {
          ModuleManager.getInstance(project).modules
            .mapNotNull { AndroidModuleInfo.getInstance(it)?.`package` }
            .toList()
        }
        Disposer.register(workbench, processes)

        processes.addSelectedProcessListeners {
          // Reset notification bar every time active process changes, since otherwise we might leave up stale notifications from an error
          // encountered during a previous run.
          InspectorBannerService.getInstance(project).notification = null
        }

        val launcher = InspectorClientLauncher.createDefaultLauncher(adb, processes, model, workbench)
        val layoutInspector = LayoutInspector(launcher, model)
        DataManager.registerDataProvider(workbench, dataProviderForLayoutInspector(layoutInspector))

        val deviceViewPanel = DeviceViewPanel(processes, layoutInspector, viewSettings, workbench)
        workbench.init(deviceViewPanel, layoutInspector, listOf(
          LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition()), false)

        project.messageBus.connect(workbench).subscribe(ToolWindowManagerListener.TOPIC,
                                                        LayoutInspectorToolWindowManagerListener(project, layoutInspector))
      }
    }
  }
}

/**
 * Listen to state changes for the create layout inspector tool window.
 */
@VisibleForTesting
class LayoutInspectorToolWindowManagerListener(private val project: Project,
                                               private val inspector: LayoutInspector)
  : ToolWindowManagerListener {
  private var wasWindowVisible = false
  private var wasMinimizedMessageShown = false

  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val window = toolWindowManager.getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID) ?: return
    val isWindowVisible = window.isVisible // Layout Inspector tool window is expanded.
    val windowVisibilityChanged = isWindowVisible != wasWindowVisible
    wasWindowVisible = isWindowVisible
    if (windowVisibilityChanged) {
      if (isWindowVisible) {
        LayoutInspectorMetrics.create(project).logEvent(DynamicLayoutInspectorEventType.OPEN)
      }
      else if (inspector.currentClient.isConnected && !wasMinimizedMessageShown) {
        wasMinimizedMessageShown = true
        toolWindowManager.notifyByBalloon(LAYOUT_INSPECTOR_TOOL_WINDOW_ID, MessageType.INFO,
                                          "<b>Layout Inspection</b> is running in the background.<br>" +
                                          "To stop it, open the <b>Layout Inspector</b> window and select <b>Stop Inspector</b> from " +
                                          "the process dropdown menu."
        )
      }
    }
  }
}
