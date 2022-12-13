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
import com.android.adblib.AdbInputChannel
import com.android.adblib.DEFAULT_SHELL_BUFFER_SIZE
import com.android.adblib.DeviceSelector
import com.android.adblib.INFINITE_DURATION
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.shellCommand
import com.android.adblib.withLineCollector
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor.Companion.LOGGER
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private const val MAX_PIDS = 1000

private const val DEVICE_PROCESSES_UPDATE_INTERVAL_MS = 2000L

/**
 * Monitors a device and keeps track of process names.
 *
 * Some process information is kept even after they terminate.
 *
 * @param parentDisposable The parent [Disposable] that controls lifecycle of this instance
 * @param parentScope The parent coroutine scope used to launch coroutines in
 * @param device The [IDevice] to monitor
 * @param flows A flow where [ProcessNames] are sent to
 * @param maxPidsBeforeEviction The maximum number of entries in the cache before we start evicting dead processes
 */
internal class ProcessNameClientMonitor(
  parentDisposable: Disposable,
  parentScope: CoroutineScope,
  private val device: IDevice,
  private val flows: ProcessNameMonitorFlows,
  private val adbDeviceServicesFactory: () -> AdbDeviceServices,
  private val maxPidsBeforeEviction: Int = MAX_PIDS,
) : Disposable {
  /**
   * The map of pid -> [ProcessNames] for currently alive processes, plus recently terminated processes.
   */
  private val processes = ConcurrentHashMap<Int, ProcessNames>()
  private val deviceProcessUpdater = DeviceProcessUpdater()
  private val coroutineScope = parentScope.createChildScope(parentDisposable = this)

  init {
    Disposer.register(parentDisposable, this)
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
    coroutineScope.launch {
      while (true) {
        deviceProcessUpdater.updateNow()
        delay(DEVICE_PROCESSES_UPDATE_INTERVAL_MS)
      }
    }
  }

  fun getProcessNames(pid: Int): ProcessNames? = processes[pid] ?: deviceProcessUpdater.getPidName(pid)

  override fun dispose() {}

  private inner class DeviceProcessUpdater {
    private val lastKnownPids = AtomicReference(mapOf<Int, ProcessNames>())

    /**
     * A copy of [AdbDeviceServices.shellAsLines] that forces the use of legacy shell rather than shell-v2.
     * It is inlined here and made private since we don't want this to be generally used.
     */
    private fun AdbDeviceServices.legacyShellAsLines(
      device: DeviceSelector,
      command: String,
      stdinChannel: AdbInputChannel? = null,
      commandTimeout: Duration = INFINITE_DURATION,
      bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
    ): Flow<ShellCommandOutputElement> {
      val cmd = shellCommand(device, command)
        .withLineCollector()
        .withStdin(stdinChannel)
        .withCommandTimeout(commandTimeout)
        .withBufferSize(bufferSize)

      if (StudioFlags.ADBLIB_LEGACY_SHELL_FOR_PS_MONITOR.get()) {
        cmd
          .allowShellV2(false)
          .allowLegacyShell(true)
      }
      return cmd.execute()
    }

    suspend fun updateNow() {
      try {
        val names = mutableMapOf<Int, ProcessNames>()
        adbDeviceServicesFactory().legacyShellAsLines(DeviceSelector.fromSerialNumber(device.serialNumber),
                                                      "ps -A -o PID,NAME").collect shellAsLines@{
          //TODO: Check for `stderr` and `exitCode` to report errors
          if (it is ShellCommandOutputElement.StdoutLine) {
            val split = it.contents.trim().split(" ")
            val pid = split[0].toIntOrNull() ?: return@shellAsLines
            val processName = split[1]
            names[pid] = ProcessNames("", processName)
          }
        }
        LOGGER.debug("${device.serialNumber}: Adding ${names.size} processes from ps command")
        lastKnownPids.set(names)
      }
      catch (e: Throwable) {
        LOGGER.warn("Error listing device processes", e)
        // We have no idea what error to expect here and how long this may last, so safer to discard old data.
        lastKnownPids.set(mapOf())
      }
    }

    fun getPidName(pid: Int): ProcessNames? = lastKnownPids.get()[pid]
  }
}
