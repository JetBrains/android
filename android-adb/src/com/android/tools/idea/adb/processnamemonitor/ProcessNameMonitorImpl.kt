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
package com.android.tools.idea.adb.processnamemonitor

import com.android.adblib.AdbDeviceServices
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbAdapterImpl
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Disconnected
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor.Companion.LOGGER
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.concurrency.createChildScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of ProcessNameMonitor
 */
internal class ProcessNameMonitorImpl @TestOnly @NonInjectable internal constructor(
  private val project: Project,
  private val flows: ProcessNameMonitorFlows,
  parentScope: CoroutineScope,
  private val adbDeviceServicesFactory: () -> AdbDeviceServices,
) : ProcessNameMonitor, Disposable {

  @Suppress("unused") // Actually used by the getService() mechanism
  constructor(project: Project)
    : this(
    project,
    ProcessNameMonitorFlowsImpl(AdbAdapterImpl(project)),
    project.coroutineScope,
    { AdbLibService.getInstance(project).session.deviceServices }
  )

  private val coroutineScope = parentScope.createChildScope(parentDisposable = this)

  @Volatile
  private var isStarted = false

  // Connected devices.
  private val devices = ConcurrentHashMap<String, ProcessNameClientMonitor>()

  init {
    Disposer.register(project, this)
  }

  override fun dispose() {}

  override fun start() {
    if (isStarted) {
      return
    }
    synchronized(this) {
      if (isStarted) {
        return
      }
      isStarted = true
    }
    coroutineScope.launch {
      flows.trackDevices().collect {
        when (it) {
          is DeviceMonitorEvent.Online -> addDevice(it.device)
          is Disconnected -> removeDevice(it.device)
        }
      }
    }
  }

  override fun getProcessNames(serialNumber: String, pid: Int): ProcessNames? {
    return devices[serialNumber]?.getProcessNames(pid)
  }

  private fun addDevice(device: IDevice) {
    LOGGER.info("Adding ${device.serialNumber}")
    devices[device.serialNumber] = ProcessNameClientMonitor(project, coroutineScope, device, flows, adbDeviceServicesFactory).apply {
      start()
    }
  }

  private fun removeDevice(device: IDevice) {
    LOGGER.info("Removing ${device.serialNumber}: ${System.identityHashCode(device)}")
    val clientMonitor = devices.remove(device.serialNumber)
    if (clientMonitor != null) {
      Disposer.dispose(clientMonitor)
    }
  }
}

