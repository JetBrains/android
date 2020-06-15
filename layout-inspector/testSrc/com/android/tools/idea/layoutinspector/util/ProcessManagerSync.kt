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
 * A class for synchronizing threads in tests.
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
class ProcessManagerSync(private val manager: InspectorProcessManager) {

  fun waitUntilReady(deviceId: String, vararg processIds: Int) {
    val expectedIdSet = processIds.toSet()
    val startTime = System.currentTimeMillis()
    while (!timeout(startTime)) {
      if (match(deviceId, expectedIdSet)) {
        return
      }
      Thread.sleep(100)
    }
    error("Timeout waiting for process changes from InspectorProcessManager")
  }

  private fun timeout(startTime: Long): Boolean =
    (System.currentTimeMillis() - startTime) > TimeUnit.SECONDS.toMillis(10)

  private fun match(deviceId: String, processIds: Set<Int>): Boolean {
    val stream = manager.getStreams().firstOrNull { it.device.serial == deviceId } ?: return processIds.isEmpty()
    val ids = manager.getProcesses(stream).map { it.pid }.toSet()
    return ids == processIds
  }
}
