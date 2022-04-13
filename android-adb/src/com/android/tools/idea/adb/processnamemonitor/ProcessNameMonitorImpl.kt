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

import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbAdapterImpl
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Disconnected
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor.Companion.LOGGER
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Implementation of ProcessNameMonitor
 */
internal open class ProcessNameMonitorImpl @TestOnly @NonInjectable internal constructor(
  private val project: Project,
  private val flows: ProcessNameMonitorFlows,
  private val coroutineContext: CoroutineContext,
) : ProcessNameMonitor {

  @Suppress("unused") // Actually used by the getService() mechanism
  constructor(project: Project) : this(project, ProcessNameMonitorFlowsImpl(AdbAdapterImpl(project)), EmptyCoroutineContext)

  private val coroutineScope: CoroutineScope = AndroidCoroutineScope(project, coroutineContext)

  @Volatile
  private var isStarted = false

  // Connected devices.
  private val devices = ConcurrentHashMap<String, ProcessNameClientMonitor>()

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

  override fun getProcessNames(device: IDevice, pid: Int): ProcessNames? {
    return devices[device.serialNumber]?.getProcessNames(pid)
  }

  private fun addDevice(device: IDevice) {
    LOGGER.info("Adding ${device.serialNumber}")
    devices[device.serialNumber] = ProcessNameClientMonitor(project, device, flows, coroutineContext).apply { start() }
  }

  private fun removeDevice(device: IDevice) {
    LOGGER.info("Removing ${device.serialNumber}: ${System.identityHashCode(device)}")
    val clientMonitor = devices.remove(device.serialNumber)
    if (clientMonitor != null) {
      Disposer.dispose(clientMonitor)
    }
  }
}

