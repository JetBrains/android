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

import com.android.adblib.ConnectedDevice
import com.android.adblib.serialNumber
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerController
import com.android.tools.idea.device.explorer.files.adbimpl.AdbDeviceFileSystem
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorController
import com.android.tools.idea.device.explorer.ui.DeviceExplorerView
import com.android.tools.idea.device.explorer.ui.DeviceExplorerViewListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.jetbrains.ide.PooledThreadExecutor

@UiThread
class DeviceExplorerController(
  project: Project,
  private val model: DeviceExplorerModel,
  private val view: DeviceExplorerView,
  private val deviceFilesController: DeviceFileExplorerController,
  private val deviceMonitorController: DeviceMonitorController) : Disposable {

  private val uiThreadScope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)
  private val viewListener: DeviceExplorerViewListener = ViewListener()

  private val edtExecutor = FutureCallbackExecutor(EdtExecutorService.getInstance())
  private val dispatcher = PooledThreadExecutor.INSTANCE.asCoroutineDispatcher()

  init {
    Disposer.register(project, this)
    view.addListener(viewListener)
    project.putUserData(KEY, this)
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

  fun reportErrorFindingDevice(message: String) {
    view.reportErrorGeneric(message, IllegalStateException())
  }

  fun selectActiveDevice(serialNumber: String) {
    uiThreadScope.launch {
      when (val device = model.devices.value.find { it.state.connectedDevice?.serialNumber == serialNumber }) {
        null -> reportErrorFindingDevice("Unable to find device with serial number $serialNumber. Please retry.")
        else -> setActiveDevice(device)
      }
    }
  }

  private fun setActiveDevice(deviceHandle: DeviceHandle?) {
    model.setActiveDevice(deviceHandle)
    deviceMonitorController.setActiveConnectedDevice(deviceHandle?.state?.connectedDevice?.serialNumber)
    deviceFilesController.setActiveConnectedDevice(newDeviceFileSystem(deviceHandle, deviceHandle?.state?.connectedDevice))
  }

  private fun newDeviceFileSystem(handle: DeviceHandle?, connectedDevice: ConnectedDevice?): AdbDeviceFileSystem?  =
    if (handle != null && connectedDevice != null) AdbDeviceFileSystem(handle, connectedDevice, edtExecutor, dispatcher) else null

  private inner class ViewListener : DeviceExplorerViewListener {
    override fun noDeviceSelected() {
      setActiveDevice(null)
    }

    override fun deviceSelected(deviceHandle: DeviceHandle) {
      setActiveDevice(deviceHandle)
    }
  }

  companion object {
    private val KEY = Key.create<DeviceExplorerController>(
      DeviceExplorerController::class.java.name
    )
    @JvmStatic
    fun getProjectController(project: Project?): DeviceExplorerController? {
      return project?.getUserData(KEY)
    }
  }
}