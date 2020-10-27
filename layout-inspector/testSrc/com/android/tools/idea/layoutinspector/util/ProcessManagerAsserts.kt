/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.util

import com.android.tools.idea.layoutinspector.transport.InspectorProcessManager
import java.util.concurrent.TimeUnit

/**
 * Asserts for a [InspectorProcessManager].
 *
 * Start by calling:
 *   val sync = ProcessManagerSync(ProcessManager)
 * Then:
 *   sync.waitUntilReady(deviceId, processIds...)
 *
 * The wait call will wait until at the specified device has exactly the
 * processes registered in the process manager.
 * Total timeout: 10secs where after an error is raised.
 */
class ProcessManagerAsserts(private val manager: InspectorProcessManager) {

  /**
   * Asserts that the specified [deviceId] is registered with exactly the processes specified by [processIds].
   *
   * This method will wait up to 10 seconds for the process manager to achieve this state.
   * An optional [operation] will done before a match is attempted.
   */
  fun assertDeviceWithProcesses(deviceId: String, vararg processIds: Int, operation: () -> Unit = {}) {
    val expectedIdSet = processIds.toSet()
    val startTime = System.currentTimeMillis()
    var found: Set<Int>? = null
    while (!timeout(startTime)) {
      operation()
      found = findProcessesOfDevice(deviceId)
      if (found == expectedIdSet) {
        return
      }
      Thread.sleep(100)
    }
    error("Timeout waiting for process changes. \n" +
          "Found: ${found?.joinToString() ?: "<Device Not Found>"} Expected: ${expectedIdSet.joinToString()}")
  }

  /**
   * Asserts that there are no devices registered.
   *
   * This method will wait up to 10 seconds for the process manager to achieve this state.
   */
  fun assertNoDevices() {
    val startTime = System.currentTimeMillis()
    var found = emptySequence<String>()
    while (!timeout(startTime)) {
      found = manager.getStreams().map { it.device.serial }
      if (found.none()) {
        return
      }
      Thread.sleep(100)
    }
    error("Timeout waiting for devices to be removed from InspectorProcessManager, found: ${found.joinToString()}")
  }

  private fun timeout(startTime: Long): Boolean =
    (System.currentTimeMillis() - startTime) > TimeUnit.SECONDS.toMillis(10)

  private fun findProcessesOfDevice(deviceId: String): Set<Int>? {
    val stream = manager.getStreams().firstOrNull { it.device.serial == deviceId } ?: return null
    return manager.getProcesses(stream).map { it.pid }.toSet()
  }
}
