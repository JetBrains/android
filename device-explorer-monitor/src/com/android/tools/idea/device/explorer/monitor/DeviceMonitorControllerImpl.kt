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

import com.android.adblib.serialNumber
import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.IDevice
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.analytics.UsageTracker.log
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.common.DeviceExplorerControllerListener
import com.android.tools.idea.device.explorer.common.DeviceExplorerTab
import com.android.tools.idea.device.explorer.common.DeviceExplorerTabController
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorView
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider.Companion.PROJECT_APPLICATION_IDS_CHANGED_TOPIC
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceExplorerEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.swing.JComponent

@UiThread
class DeviceMonitorControllerImpl(
  private val project: Project,
  private val model: DeviceMonitorModel,
  private val view: DeviceMonitorView,
  private val deviceService: DeviceService
): Disposable, DeviceExplorerTabController {

  private val uiThreadScope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)
  private val setupJob = CompletableDeferred<Unit>()
  private val deviceServiceListener = ModelDeviceServiceListener()
  private val viewListener = ViewListener()
  override var controllerListener: DeviceExplorerControllerListener? = null

  init {
    Disposer.register(project, this)
    project.putUserData(KEY, this)
  }

  override fun setup() {
    view.addListener(viewListener)
    deviceService.addListener(deviceServiceListener)
    view.setup()
    view.trackModelChanges(uiThreadScope)

    uiThreadScope.launch {
      model.projectApplicationIdListChanged()
      try {
        deviceService.start()
        setupJob.complete(Unit)
      }
      catch (t: Throwable) {
        setupJob.completeExceptionally(t)
      }
    }

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
    val serialNumber = deviceHandle?.state?.connectedDevice?.serialNumber
    uiThreadScope.launch {
      val iDevice = deviceService.getIDeviceFromSerialNumber(serialNumber)
      model.activeDeviceChanged(iDevice)
    }
  }

  override fun getViewComponent(): JComponent = view.panelComponent

  override fun getTabName(): String = DeviceExplorerTab.Processes.name

  override fun setPackageFilter(isActive: Boolean) {
    uiThreadScope.launch {
      model.setPackageFilter(isActive)
    }
  }

  override fun dispose() {
    view.removeListener(viewListener)
    deviceService.removeListener(deviceServiceListener)
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

  private inner class ModelDeviceServiceListener : DeviceServiceListener {
    override fun deviceProcessListUpdated(device: IDevice) {
      uiThreadScope.launch {
        model.refreshProcessListForDevice(device)
      }
    }
  }

  @UiThread
  private inner class ViewListener : DeviceMonitorViewListener {
    override fun refreshInvoked() {
      uiThreadScope.launch {
        model.refreshCurrentProcessList()
        trackAction(DeviceExplorerEvent.Action.REFRESH_PROCESSES)
      }
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