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

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.appinspection.api.process.ProcessNotifier
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.tree.TreeSettingsImpl
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.DeviceViewSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.ui.enableLiveLayoutInspector
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.ide.DataManager
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
import java.util.concurrent.Executor
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

const val LAYOUT_INSPECTOR_TOOL_WINDOW_ID = "Layout Inspector"

val LAYOUT_INSPECTOR_DATA_KEY = DataKey.create<LayoutInspector>(LayoutInspector::class.java.name)

/**
 * Create a [DataProvider] for the specified [layoutInspector].
 */
fun dataProviderForLayoutInspector(layoutInspector: LayoutInspector, deviceViewPanel: DataProvider): DataProvider =
  DataProvider { dataId -> if (LAYOUT_INSPECTOR_DATA_KEY.`is`(dataId)) layoutInspector else deviceViewPanel.getData(dataId) }

/**
 * Return true if the process it represents is inspectable in the Layout Inspector.
 *
 * Currently, a process is deemed inspectable if the device it's running on is M+ and if it's debuggable. The latter condition is
 * guaranteed to be true because transport pipeline only provides debuggable processes, so there is no need to check.
 */
private fun ProcessDescriptor.isInspectableInLayoutInspector(): Boolean {
  return this.device.apiLevel >= AndroidVersion.VersionCodes.M
}

/**
 * ToolWindowFactory: For creating a layout inspector tool window for the project.
 */
class LayoutInspectorToolWindowFactory : ToolWindowFactory {

  override fun isApplicable(project: Project): Boolean = enableLiveLayoutInspector

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
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

        val processes = createProcessesModel(project, AppInspectionDiscoveryService.instance.apiServices.processNotifier, edtExecutor)
        Disposer.register(workbench, processes)
        val model = InspectorModel(project)
        model.setProcessModel(processes)

        processes.addSelectedProcessListeners {
          // Reset notification bar every time active process changes, since otherwise we might leave up stale notifications from an error
          // encountered during a previous run.
          InspectorBannerService.getInstance(project).notification = null
        }

        lateinit var launcher: InspectorClientLauncher
        val treeSettings = TreeSettingsImpl { launcher.activeClient }
        val stats = SessionStatistics(model, treeSettings)
        launcher = InspectorClientLauncher.createDefaultLauncher(adb, processes, model, stats, workbench)
        val layoutInspector = LayoutInspector(launcher, model, stats, treeSettings)
        val deviceViewPanel = DeviceViewPanel(processes, layoutInspector, viewSettings, workbench)
        DataManager.registerDataProvider(workbench, dataProviderForLayoutInspector(layoutInspector, deviceViewPanel))
        workbench.init(deviceViewPanel, layoutInspector, listOf(
          LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition()), false)

        project.messageBus.connect(workbench).subscribe(ToolWindowManagerListener.TOPIC,
                                                        LayoutInspectorToolWindowManagerListener(project, toolWindow, deviceViewPanel,
                                                                                                 launcher))
      }
    }
  }

  @VisibleForTesting
  fun createProcessesModel(project: Project, processNotifier: ProcessNotifier, executor: Executor) = ProcessesModel(
    executor = executor,
    processNotifier = processNotifier,
    acceptProcess = { it.isInspectableInLayoutInspector() },
    getPreferredProcessNames = {
      ModuleManager.getInstance(project).modules
        .mapNotNull { AndroidModuleInfo.getInstance(it)?.`package` }
        .toList()
    }
  )
}

/**
 * Listen to state changes for the create layout inspector tool window.
 */
class LayoutInspectorToolWindowManagerListener @VisibleForTesting constructor(private val project: Project,
                                                                              private val clientLauncher: InspectorClientLauncher,
                                                                              private val stopInspectors: () -> Unit = {},
                                                                              private var wasWindowVisible: Boolean = false
) : ToolWindowManagerListener {

  internal constructor(project: Project,
                       toolWindow: ToolWindow,
                       deviceViewPanel: DeviceViewPanel,
                       clientLauncher: InspectorClientLauncher
  ) : this(project, clientLauncher, { deviceViewPanel.stopInspectors() }, toolWindow.isVisible)

  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val window = toolWindowManager.getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID) ?: return
    val isWindowVisible = window.isVisible // Layout Inspector tool window is expanded.
    val windowVisibilityChanged = isWindowVisible != wasWindowVisible
    wasWindowVisible = isWindowVisible
    if (windowVisibilityChanged) {
      if (isWindowVisible) {
        LayoutInspectorMetrics.create(project).logEvent(DynamicLayoutInspectorEventType.OPEN)
      }
      else if (clientLauncher.activeClient.isConnected) {
        toolWindowManager.notifyByBalloon(
          LAYOUT_INSPECTOR_TOOL_WINDOW_ID,
          MessageType.INFO,
          """
            <b>Layout Inspection</b> is running in the background.<br>
            You can either <a href="stop">stop</a> it, or leave it running and resume your session later.
          """.trimIndent(),
          null
        ) { hyperlinkEvent ->
          if (hyperlinkEvent.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            stopInspectors()
          }
        }
      }
      clientLauncher.enabled = isWindowVisible
    }
  }
}
