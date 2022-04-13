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
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor.Companion.LOGGER
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

private const val MAX_PIDS = 1000

/**
 * Monitors a device and keeps track of process names.
 *
 * Some process information is kept even after they terminate.
 *
 * @param project The [Project]
 * @param device The [IDevice] to monitor
 * @param flows A flow where [ProcessNames] are sent to.
 * @param coroutineScope Optional scope to run coroutines in
 * @param maxPidsBeforeEviction The maximum number of entries in the cache before we start evicting dead processes
 */
internal class ProcessNameClientMonitor @TestOnly internal constructor(
  project: Project,
  private val device: IDevice,
  private val flows: ProcessNameMonitorFlows,
  coroutineScope: CoroutineScope?,
  private val maxPidsBeforeEviction: Int,
) : Disposable {

  constructor(project: Project, device: IDevice, flows: ProcessNameMonitorFlows) : this(project, device, flows, null, MAX_PIDS)

  /**
   * The map of pid -> [ProcessNames] for currently alive processes, plus recently terminated processes.
   */
  private val processes = ConcurrentHashMap<Int, ProcessNames>()

  private val coroutineScope: CoroutineScope = coroutineScope ?: AndroidCoroutineScope(this)

  init {
    Disposer.register(project, this)
  }

  fun start() {
    coroutineScope.launch {
      // Set of pids corresponding to processes that were (recently) terminated and
      // are candidates to be removed from [processes] when the latter is full.
      val evictionList = LinkedHashSet<Int>()

      flows.trackClients(device).collect { (addedProcesses, removedProcesses) ->
        // All terminated processes immediately become candidates for eviction
        evictionList.addAll(removedProcesses)

        // New processes are stored in the map and removed from (dead processes) eviction list
        addedProcesses.forEach { (pid, names) ->
          LOGGER.debug("${device.serialNumber}: Adding client $pid -> $names")
          processes[pid] = names
          // Do not evict a pid that is being reused
          evictionList.remove(pid)
        }

        // When we have more than maxPidsBeforeEviction entries in the cache, try to evict dead processes.
        // Note that we can still end up with more than maxPidsBeforeEviction if they are all still active.
        while (processes.size > maxPidsBeforeEviction && evictionList.isNotEmpty()) {
          val pid = evictionList.first()
          evictionList.remove(pid)
          val evicted = processes.remove(pid)
          LOGGER.debug("${device.serialNumber}: Evicting $pid -> $evicted")
        }
      }
    }
  }

  fun getProcessNames(pid: Int): ProcessNames? = processes[pid]

  override fun dispose() {}
}
