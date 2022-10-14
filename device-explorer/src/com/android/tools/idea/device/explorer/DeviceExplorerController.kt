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
package com.android.tools.idea.device.explorer

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerController
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorController
import com.android.tools.idea.device.explorer.ui.DeviceExplorerView
import com.android.tools.idea.device.explorer.ui.DeviceExplorerViewListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DeviceExplorerController(
  project: Project,
  private val model: DeviceExplorerModel,
  private val view: DeviceExplorerView,
  private val deviceFilesController: DeviceFileExplorerController,
  private val deviceMonitorController: DeviceMonitorController) : Disposable {

  private val uiThreadScope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)
  private val viewListener: DeviceExplorerViewListener = ViewListener()

  init {
    Disposer.register(project, this)
    view.addListener(viewListener)
  }

  override fun dispose() {
    view.removeListener(viewListener)
  }

  fun setup() {
    uiThreadScope.launch {
      view.setup()
      deviceFilesController.setup()
      view.addTab(deviceFilesController.getViewComponent(), "Files")
      deviceMonitorController.setup()
      view.addTab(deviceMonitorController.getViewComponent(), "Processes")
      launch { view.trackDeviceListChanges() }
      launch { view.trackActiveDeviceChanges() }
    }
  }

  private inner class ViewListener : DeviceExplorerViewListener {
    override fun noDeviceSelected() {
      model.setActiveDevice(null)
      deviceMonitorController.activeDeviceChanged(null)
      deviceFilesController.setActiveConnectedDevice(null, null)
    }

    override fun deviceSelected(deviceHandle: DeviceHandle) {
      model.setActiveDevice(deviceHandle)
      deviceMonitorController.activeDeviceChanged(deviceHandle.state.connectedDevice)
      deviceFilesController.setActiveConnectedDevice(deviceHandle, deviceHandle.state.connectedDevice)
    }
  }
}