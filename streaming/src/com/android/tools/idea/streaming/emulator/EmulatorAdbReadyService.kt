/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.adblib.serialNumber
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.launch
import java.awt.EventQueue

/**
 * Returns true if the ADB is ready to handle commands for the device with [serialNumber] in [project].
 */
internal fun isReadyForAdbCommands(project: Project, serialNumber: String): Boolean =
  project.service<EmulatorAdbReadyService>().isReadyForAdbCommands(serialNumber)

@Service(Service.Level.PROJECT)
internal class EmulatorAdbReadyService(private val project: Project): Disposable {
  private val deviceHandleMap = ConcurrentCollectionFactory.createConcurrentMap<String, DeviceHandle>()
  private val scope = AndroidCoroutineScope(this)

  override fun dispose() {
  }

  init {
    scope.launch {
      val deviceProvisioner = project.getService(DeviceProvisionerService::class.java).deviceProvisioner
      deviceProvisioner.devices.collect { devices ->
        devices.forEach { device ->
          scope.launch {
            device.stateFlow.collect {
              device.update()
            }
          }
        }
      }
    }
  }

  fun isReadyForAdbCommands(serialNumber: String): Boolean =
    deviceHandleMap[serialNumber]?.state?.isReady ?: false

  private fun DeviceHandle.update() {
    val connectedDevice = state.connectedDevice
    if (connectedDevice != null) {
      deviceHandleMap[connectedDevice.serialNumber] = this
      if (state.isReady) {
        EventQueue.invokeLater { updateToolbar(connectedDevice.serialNumber) }
      }
    }
    else {
      deviceHandleMap.values.remove(this)
    }
  }

  private fun updateToolbar(serialNumber: String) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return
    toolWindow.contentManager.contents.map { it.component }
      .filterIsInstance<EmulatorToolWindowPanel>()
      .filter { it.emulator.emulatorId.serialNumber == serialNumber }
      .forEach { it.updateMainToolbar() }
  }
}
