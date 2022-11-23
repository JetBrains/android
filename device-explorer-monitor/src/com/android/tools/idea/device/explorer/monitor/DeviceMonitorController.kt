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

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorView
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.swing.JComponent

@UiThread
class DeviceMonitorController(
  project: Project,
  private val model: DeviceMonitorModel,
  private val view: DeviceMonitorView,
  private val deviceService: DeviceService
): Disposable {

  private val uiThreadScope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)
  private val setupJob = CompletableDeferred<Unit>()
  private val deviceServiceListener = ModelDeviceServiceListener()
  private val viewListener = ViewListener()

  init {
    Disposer.register(project, this)
    project.putUserData(KEY, this)
  }

  fun setActiveConnectedDevice(serialNumber: String?) {
    uiThreadScope.launch {
      val iDevice = deviceService.getIDeviceFromSerialNumber(serialNumber)
      model.activeDeviceChanged(iDevice)
    }
  }

  fun setup() {
    view.addListener(viewListener)
    deviceService.addListener(deviceServiceListener)
    view.setup()

    uiThreadScope.launch {
      try {
        deviceService.start()
        setupJob.complete(Unit)
      }
      catch (t: Throwable) {
        setupJob.completeExceptionally(t)
      }
    }
  }

  fun getViewComponent(): JComponent = view.panelComponent

  override fun dispose() {
    view.removeListener(viewListener)
    deviceService.removeListener(deviceServiceListener)
    uiThreadScope.cancel("${javaClass.simpleName} has been disposed")
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
      }
    }

    override fun killNodesInvoked(nodes: List<ProcessTreeNode>) {
      uiThreadScope.launch {
        model.killNodesInvoked(nodes)
      }
    }

    override fun forceStopNodesInvoked(nodes: List<ProcessTreeNode>) {
      uiThreadScope.launch {
        model.forceStopNodesInvoked(nodes)
      }
    }
  }

  companion object {
    private val KEY = Key.create<DeviceMonitorController>(
      DeviceMonitorController::class.java.name
    )

    @JvmStatic
    fun getProjectController(project: Project?): DeviceMonitorController? {
      return project?.getUserData(KEY)
    }
  }
}