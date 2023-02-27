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

import com.android.adblib.AdbLogger
import com.android.adblib.AdbSession
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbAdapter
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Disconnected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of ProcessNameMonitor
 */
internal class ProcessNameMonitorImpl @TestOnly internal constructor(
  parentScope: CoroutineScope,
  private val adbSession: AdbSession,
  private val flows: ProcessNameMonitorFlows,
  private val logger: AdbLogger,
) : ProcessNameMonitor, Closeable {

  constructor(parentScope: CoroutineScope, adbSession: AdbSession, adbAdapter: AdbAdapter, logger: AdbLogger)
    : this(parentScope, adbSession, ProcessNameMonitorFlowsImpl(adbAdapter, logger, adbSession.ioDispatcher), logger)

  private val scope: CoroutineScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

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
    scope.launch {
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

  override fun close() {
    scope.cancel()
  }

  private fun addDevice(device: IDevice) {
    logger.info { "Adding ${device.serialNumber}" }
    devices[device.serialNumber] = ProcessNameClientMonitor(scope, device, flows, adbSession, logger).apply {
      start()
    }
  }

  private fun removeDevice(device: IDevice) {
    logger.info { ("Removing ${device.serialNumber}: ${System.identityHashCode(device)}") }
    val clientMonitor = devices.remove(device.serialNumber)
    clientMonitor?.close()
  }
}
