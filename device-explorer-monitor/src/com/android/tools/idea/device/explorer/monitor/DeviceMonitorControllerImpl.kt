/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.monitor

import com.android.adblib.ConnectedDevice
import com.android.adblib.scope
import com.android.adblib.tools.debugging.JdwpProcessChange
import com.android.adblib.tools.debugging.jdwpProcessChangeFlow
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.analytics.UsageTracker.log
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.common.DeviceExplorerControllerListener
import com.android.tools.idea.device.explorer.common.DeviceExplorerTab
import com.android.tools.idea.device.explorer.common.DeviceExplorerTabController
import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import com.android.tools.idea.device.explorer.monitor.processes.toProcessInfo
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorView
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider.Companion.PROJECT_APPLICATION_IDS_CHANGED_TOPIC
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceExplorerEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JComponent

@UiThread
class DeviceMonitorControllerImpl(
  private val project: Project,
  private val model: DeviceMonitorModel,
  private val view: DeviceMonitorView
) : Disposable, DeviceExplorerTabController {

  private val uiThreadScope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)
  private val viewListener = ViewListener()
  private var activeDevice: ConnectedDevice? = null
  private var processTrackerJob: Job? = null
  override var controllerListener: DeviceExplorerControllerListener? = null

  init {
    Disposer.register(project, this)
    project.putUserData(KEY, this)
  }

  override fun setup() {
    view.addListener(viewListener)
    view.setup()
    view.trackModelChanges(uiThreadScope)

    project.messageBus.connect(this).subscribe(
      PROJECT_APPLICATION_IDS_CHANGED_TOPIC,
      ProjectApplicationIdsProvider.ProjectApplicationIdsListener {
        uiThreadScope.launch {
          model.projectApplicationIdListChanged()
        }
      }
    )
  }

  override fun setActiveConnectedDevice(deviceHandle: DeviceHandle?) {
    activeDevice = deviceHandle?.state?.connectedDevice
    model.setActiveDevice(activeDevice)
    startDebuggableProcessTracking()
  }

  private fun startDebuggableProcessTracking() {
    processTrackerJob?.cancel()
    processTrackerJob = null
    model.setAllProcesses(listOf())
    activeDevice?.let { currentDevice ->
      processTrackerJob = currentDevice.scope.launch {
        val allProcesses = mutableMapOf<Int, ProcessInfo>()
        currentDevice.jdwpProcessChangeFlow.collect { processChange ->
          when (processChange) {
            is JdwpProcessChange.Added ->
              allProcesses[processChange.processInfo.properties.pid] = processChange.processInfo.toProcessInfo()

            is JdwpProcessChange.Updated ->
              allProcesses[processChange.processInfo.properties.pid] = processChange.processInfo.toProcessInfo()

            is JdwpProcessChange.Removed ->
              allProcesses.remove(processChange.processInfo.properties.pid)
          }
          withContext(AndroidDispatchers.uiThread) {
            model.setAllProcesses(allProcesses.values.toList())
          }
        }
      }
    }
  }

  override fun getViewComponent(): JComponent = view.panelComponent

  override fun getTabName(): String = DeviceExplorerTab.Processes.name

  override fun setPackageFilter(isActive: Boolean) {
    model.setPackageFilter(isActive)
  }

  override fun dispose() {
    view.removeListener(viewListener)
    processTrackerJob?.cancel()
    uiThreadScope.cancel("${javaClass.simpleName} has been disposed")
  }

  private fun trackAction(action: DeviceExplorerEvent.Action) {
    log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.DEVICE_EXPLORER)
        .setDeviceExplorerEvent(
          DeviceExplorerEvent.newBuilder()
            .setAction(action)
        )
    )
  }

  @UiThread
  private inner class ViewListener : DeviceMonitorViewListener {
    override fun refreshInvoked() {
      startDebuggableProcessTracking()
      trackAction(DeviceExplorerEvent.Action.REFRESH_PROCESSES)
    }

    override fun killNodesInvoked(rows: IntArray) {
      uiThreadScope.launch {
        model.killNodesInvoked(rows)
        trackAction(DeviceExplorerEvent.Action.KILL)
      }
    }

    override fun forceStopNodesInvoked(rows: IntArray) {
      uiThreadScope.launch {
        model.forceStopNodesInvoked(rows)
        trackAction(DeviceExplorerEvent.Action.FORCE_STOP)
      }
    }

    override fun debugNodes(rows: IntArray) {
      uiThreadScope.launch {
        model.debugNodesInvoked(project, rows)
        trackAction(DeviceExplorerEvent.Action.ATTACH_DEBUGGER)
      }
    }

    override fun packageFilterToggled(isActive: Boolean) {
      controllerListener?.packageFilterToggled(isActive)
    }

    override fun backupApplication(rows: IntArray) {
      uiThreadScope.launch {
        model.backupApplication(project, rows)
        trackAction(DeviceExplorerEvent.Action.BACKUP_APP_DATA_CLICKED)
      }
    }

    override fun restoreApplication(rows: IntArray) {
      uiThreadScope.launch {
        model.restoreApplication(project, rows)
        trackAction(DeviceExplorerEvent.Action.RESTORE_APP_DATA_CLICKED)
      }
    }
  }

  companion object {
    private val KEY = Key.create<DeviceMonitorControllerImpl>(
      DeviceMonitorControllerImpl::class.java.name
    )

    @JvmStatic
    fun getProjectController(project: Project?): DeviceMonitorControllerImpl? {
      return project?.getUserData(KEY)
    }
  }
}