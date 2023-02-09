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
import com.android.tools.idea.appinspection.api.process.ProcessDiscovery
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.appinspection.ide.ui.RecentProcess
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.TransportErrorListener
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcess
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetection
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetectionInitializer
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessListener
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.stopInspector
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.tree.InspectorTreeSettings
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.layoutinspector.ui.InspectorRenderSettings
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

const val LAYOUT_INSPECTOR_TOOL_WINDOW_ID = "Layout Inspector"

/**
 * Create a [DataProvider] for the specified [layoutInspector].
 */
fun dataProviderForLayoutInspector(layoutInspector: LayoutInspector, deviceViewPanel: DataProvider): DataProvider =
  DataProvider { dataId -> if (LAYOUT_INSPECTOR_DATA_KEY.`is`(dataId)) layoutInspector else deviceViewPanel.getData(dataId) }

/**
 * Class used to keep track of open projects, for metrics purposes
 */
object LayoutInspectorOpenProjectsTracker {
  internal var openProjects = 0

  fun areMultipleProjectsOpen(): Boolean = openProjects > 1
}

/**
 * ToolWindowFactory: For creating a layout inspector tool window for the project.
 */
class LayoutInspectorToolWindowFactory : ToolWindowFactory {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    // Coroutine scope tied to the lifecycle of this tool window. It will be cancelled when the tool window is disposed.
    val layoutInspectorCoroutineScope = AndroidCoroutineScope(toolWindow.disposable)

    LayoutInspectorOpenProjectsTracker.openProjects += 1
    Disposer.register(toolWindow.disposable) { LayoutInspectorOpenProjectsTracker.openProjects -= 1 }

    val workbench = WorkBench<LayoutInspector>(project, LAYOUT_INSPECTOR_TOOL_WINDOW_ID, null, toolWindow.disposable)
    val viewSettings = InspectorRenderSettings()
    val inspectorClientSettings = InspectorClientSettings(project)

    val edtExecutor = EdtExecutorService.getInstance()

    val contentPanel = JPanel(BorderLayout())
    contentPanel.add(InspectorBanner(project), BorderLayout.NORTH)
    contentPanel.add(workbench, BorderLayout.CENTER)

    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(contentPanel, "", true)
    contentManager.addContent(content)

    TransportErrorListener(project, LayoutInspectorMetrics, toolWindow.disposable)

    val processesModel = createProcessesModel(project, AppInspectionDiscoveryService.instance.apiServices.processDiscovery, edtExecutor)
    Disposer.register(workbench, processesModel)
    val executor = Executors.newScheduledThreadPool(1)
    Disposer.register(workbench) {
      executor.shutdown()
      executor.awaitTermination(3, TimeUnit.SECONDS)
    }
    val model = InspectorModel(project, executor)
    model.setProcessModel(processesModel)

    processesModel.addSelectedProcessListeners {
      // Reset notification bar every time active process changes, since otherwise we might leave up stale notifications from an error
      // encountered during a previous run.
      InspectorBannerService.getInstance(project)?.clear()
    }

    lateinit var launcher: InspectorClientLauncher
    val treeSettings = InspectorTreeSettings { launcher.activeClient }
    val metrics = LayoutInspectorSessionMetrics(project, null)
    launcher = InspectorClientLauncher.createDefaultLauncher(
      processesModel,
      model,
      metrics,
      treeSettings,
      inspectorClientSettings,
      layoutInspectorCoroutineScope,
      workbench
    )
    val layoutInspector = LayoutInspector(launcher, model, treeSettings)

    val deviceModel = DeviceModel(workbench, processesModel)
    val foregroundProcessDetection = createForegroundProcessDetection(
      project, processesModel, deviceModel, layoutInspectorCoroutineScope
    )

    val deviceViewPanel = DeviceViewPanel(
      coroutineScope = layoutInspectorCoroutineScope,
      processesModel = processesModel,
      deviceModel = deviceModel,
      onDeviceSelected = { newDevice -> foregroundProcessDetection?.startPollingDevice(newDevice) },
      onProcessSelected = { newProcess -> processesModel.selectedProcess = newProcess },
      onStopInspector = { stopInspector(project, deviceModel, processesModel, foregroundProcessDetection) },
      layoutInspector = layoutInspector,
      viewSettings = viewSettings,
      inspectorClientSettings = inspectorClientSettings,
      disposableParent = workbench
    )

    // notify DeviceViewPanel that a new foreground process showed up
    foregroundProcessDetection?.foregroundProcessListeners?.add(object : ForegroundProcessListener {
      override fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess) {
        deviceViewPanel.onNewForegroundProcess(foregroundProcess)
      }
    })

    DataManager.registerDataProvider(workbench, dataProviderForLayoutInspector(layoutInspector, deviceViewPanel))
    workbench.init(
      deviceViewPanel,
      layoutInspector,
      listOf(LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition()),
      false
    )

    project.messageBus
      .connect(workbench)
      .subscribe(ToolWindowManagerListener.TOPIC, LayoutInspectorToolWindowManagerListener(project, toolWindow, deviceViewPanel, launcher))
  }

  private fun createForegroundProcessDetection(
    project: Project,
    processesModel: ProcessesModel,
    deviceModel: DeviceModel,
    coroutineScope: CoroutineScope
  ): ForegroundProcessDetection? {
    return if (LayoutInspectorSettings.getInstance().autoConnectEnabled) {
      ForegroundProcessDetectionInitializer.initialize(
        project = project,
        processModel = processesModel,
        deviceModel = deviceModel,
        coroutineScope = coroutineScope,
        metrics = ForegroundProcessDetectionMetrics
      )
    }
    else {
      null
    }
  }

  @VisibleForTesting
  fun createProcessesModel(project: Project, processDiscovery: ProcessDiscovery, executor: Executor) = ProcessesModel(
    executor = executor,
    processDiscovery = processDiscovery,
    isPreferred = { RecentProcess.isRecentProcess(it, project) }
  )
}

/**
 * Listen to state changes for the create layout inspector tool window.
 */
class LayoutInspectorToolWindowManagerListener @VisibleForTesting constructor(private val project: Project,
                                                                              private val clientLauncher: InspectorClientLauncher,
                                                                              private val stopInspectors: () -> Unit = {},
                                                                              private var wasWindowVisible: Boolean = false,
) : ToolWindowManagerListener {

  internal constructor(project: Project,
                       toolWindow: ToolWindow,
                       deviceViewPanel: DeviceViewPanel,
                       clientLauncher: InspectorClientLauncher,
  ) : this(project, clientLauncher, { deviceViewPanel.stopInspectors() }, toolWindow.isVisible)

  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val window = toolWindowManager.getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID) ?: return
    val isWindowVisible = window.isVisible // Layout Inspector tool window is expanded.
    val windowVisibilityChanged = isWindowVisible != wasWindowVisible
    wasWindowVisible = isWindowVisible
    if (windowVisibilityChanged) {
      if (isWindowVisible) {
        LayoutInspectorMetrics.logEvent(DynamicLayoutInspectorEventType.OPEN)
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