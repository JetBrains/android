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
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.shellCommand
import com.android.adblib.withLineCollector
import com.android.ddmlib.IDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private const val MAX_PIDS = 1000

private const val DEVICE_PROCESSES_UPDATE_INTERVAL_MS = 2000L

/**
 * Monitors a device and keeps track of process names.
 *
 * Some process information is kept even after they terminate.
 *
 * @param device The [IDevice] to monitor
 * @param flows A flow where [ProcessNames] are sent to
 * @param adbSession An [AdbSession]
 * @param maxPidsBeforeEviction The maximum number of entries in the cache before we start evicting dead processes
 * @param enablePsPolling If true, use `ps` command to poll device for processes
 */
internal class ProcessNameClientMonitor(
  parentScope: CoroutineScope,
  private val device: IDevice,
  private val flows: ProcessNameMonitorFlows,
  private val adbSession: AdbSession,
  private val logger: AdbLogger,
  private val maxPidsBeforeEviction: Int = MAX_PIDS,
  enablePsPolling: Boolean = false,
) : Closeable {
  /**
   * The map of pid -> [ProcessNames] for currently alive processes, plus recently terminated processes.
   */
  private val processes = ConcurrentHashMap<Int, ProcessNames>()
  private val deviceProcessUpdater = if (enablePsPolling) DeviceProcessUpdater() else null

  private val scope: CoroutineScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

  fun start() {
    scope.launch {
      // Set of pids corresponding to processes that were (recently) terminated and
      // are candidates to be removed from [processes] when the latter is full.
      val evictionList = LinkedHashSet<Int>()

      flows.trackClients(device).collect { (addedProcesses, removedProcesses) ->
        // All terminated processes immediately become candidates for eviction
        evictionList.addAll(removedProcesses)

        // New processes are stored in the map and removed from (dead processes) eviction list
        addedProcesses.forEach { (pid, names) ->
          logger.debug { "${device.serialNumber}: Adding client $pid -> $names" }
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
          logger.debug { "${device.serialNumber}: Evicting $pid -> $evicted" }
        }
      }
    }
    if (deviceProcessUpdater != null) {
      scope.launch {
        while (true) {
          deviceProcessUpdater.updateNow()
          delay(DEVICE_PROCESSES_UPDATE_INTERVAL_MS)
        }
      }
    }
  }

  fun getProcessNames(pid: Int): ProcessNames? = processes[pid] ?: deviceProcessUpdater?.getPidName(pid)

  override fun close() {
    scope.cancel()
  }

  private inner class DeviceProcessUpdater {
    private val lastKnownPids = AtomicReference(mapOf<Int, ProcessNames>())

    suspend fun updateNow() {
      try {
        val names = mutableMapOf<Int, ProcessNames>()
        adbSession.deviceServices.shellCommand(DeviceSelector.fromSerialNumber(device.serialNumber),"ps -A -o PID,NAME")
          .withLineCollector()
          .execute()
          .collect shellAsLines@{
          //TODO: Check for `stderr` and `exitCode` to report errors
          if (it is ShellCommandOutputElement.StdoutLine) {
            val split = it.contents.trim().split(" ")
            val pid = split[0].toIntOrNull() ?: return@shellAsLines
            val processName = split[1]
            names[pid] = ProcessNames("", processName)
          }
        }
        logger.debug { "${device.serialNumber}: Adding ${names.size} processes from ps command" }
        lastKnownPids.set(names)
      }
      catch (e: Throwable) {
        logger.warn(e, "Error listing device processes")
        // We have no idea what error to expect here and how long this may last, so safer to discard old data.
        lastKnownPids.set(mapOf())
      }
    }

    fun getPidName(pid: Int): ProcessNames? = lastKnownPids.get()[pid]
  }
}
