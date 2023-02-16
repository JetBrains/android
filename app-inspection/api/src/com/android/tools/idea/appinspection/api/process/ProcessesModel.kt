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
package com.android.tools.idea.appinspection.api.process

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import java.util.concurrent.Executor
import org.jetbrains.annotations.TestOnly

/**
 * Model class that owns a list of active [ProcessDescriptor] targets with listeners that trigger
 * when one is added or removed.
 *
 * The constructor takes an [executor] which gives it affinity to a particular thread (defaulting to
 * the current thread mainly for testing, but in production, an EDT executor is more likely to be
 * useful for UI-related work). This executor will be used to respond to external process updates.
 *
 * Additionally, [selectedProcess] offers a thread-safe way to set and get the currently selected
 * process.
 *
 * Finally, there's support for stopping the model, which both terminates the current
 * [selectedProcess] (if set) and prevents further updates from being accepted, until the model is
 * resumed.
 *
 * @param acceptProcess A filter which affects which processes are added to the model. If not
 *   specified, all processes are accepted.
 */
class ProcessesModel(
  private val executor: Executor,
  private val processDiscovery: ProcessDiscovery,
  private val acceptProcess: (ProcessDescriptor) -> Boolean = { true },
  private val isPreferred: (ProcessDescriptor) -> Boolean = { false }
) : Disposable {

  @TestOnly
  constructor(
    processDiscovery: ProcessDiscovery,
    isPreferred: (ProcessDescriptor) -> Boolean = { false }
  ) : this(MoreExecutors.directExecutor(), processDiscovery, isPreferred = isPreferred)

  @TestOnly
  constructor(
    processDiscovery: ProcessDiscovery,
    acceptProcess: (ProcessDescriptor) -> Boolean,
    isPreferred: (ProcessDescriptor) -> Boolean = { false }
  ) : this(MoreExecutors.directExecutor(), processDiscovery, acceptProcess, isPreferred)

  private val lock = Any()

  @GuardedBy("lock") private val selectedProcessListeners = mutableMapOf<() -> Unit, Executor>()

  @GuardedBy("lock") private val _processes = mutableSetOf<ProcessDescriptor>()

  @GuardedBy("lock") private var _selectedProcess: ProcessDescriptor? = null

  val devices: Set<DeviceDescriptor>
    get() = processDiscovery.devices.toSet()

  val processes: Set<ProcessDescriptor>
    get() = synchronized(lock) { _processes.toSet() }

  /**
   * Returns true if [selectedProcess] auto connected.
   *
   * Ideally this should be accessed within the callback from the selectedProcessListeners
   */
  var isAutoConnected = false
    private set

  /**
   * Setting the currently selected process the side effect of firing listeners that the selected
   * process changed.
   *
   * You may set the selected process to null to clear it.
   *
   * Setting this value is a no-op if have previously called [stopIfSelected] on this model. Ideally
   * this should be accessed within the callback from the selectedProcessListeners
   */
  var selectedProcess: ProcessDescriptor?
    get() = synchronized(lock) { _selectedProcess }
    set(value) {
      setSelectedProcess(value)
    }

  private fun setSelectedProcess(process: ProcessDescriptor?, autoConnected: Boolean = false) {
    synchronized(lock) {
      if (_selectedProcess != process) {
        // While we leave processes in the list when they die, once we update the active
        // selection, we silently prune them at that point. Otherwise, dead processes would
        // continue to build up. This also has the nice effect of making it feel that when a
        // user starts running a new process, it neatly replaces the last dead one.
        _processes.removeAll { it != process && !it.isRunning }
        _selectedProcess = process
        isAutoConnected = autoConnected && (process != null)
        selectedProcessListeners.forEach { (listener, executor) -> executor.execute(listener) }
      }
    }
  }

  /** Add a listener which will be triggered with the selected process when it changes. */
  fun addSelectedProcessListeners(executor: Executor, listener: () -> Unit) {
    synchronized(lock) { selectedProcessListeners[listener] = executor }
  }

  @TestOnly
  fun addSelectedProcessListeners(listener: () -> Unit) =
    addSelectedProcessListeners(MoreExecutors.directExecutor(), listener)

  private val processListener =
    object : SimpleProcessListener() {
      override fun onProcessConnected(process: ProcessDescriptor) {
        if (!acceptProcess(process)) return

        synchronized(lock) {
          _processes.add(process)
          if (isProcessPreferred(process) && !isProcessPreferred(selectedProcess)) {
            setSelectedProcess(process, autoConnected = true)
          }
        }
      }

      override fun onProcessDisconnected(process: ProcessDescriptor) {
        if (!acceptProcess(process)) return

        synchronized(lock) {
          _processes.remove(process)
          stopIfSelected(process)
        }
      }
    }

  init {
    processDiscovery.addProcessListener(executor, processListener)
  }

  override fun dispose() {
    processDiscovery.removeProcessListener(processListener)
  }

  fun isProcessPreferred(
    processDescriptor: ProcessDescriptor?,
    includeDead: Boolean = false
  ): Boolean {
    return processDescriptor != null &&
      (processDescriptor.isRunning || includeDead) &&
      isPreferred(processDescriptor)
  }

  @GuardedBy("lock")
  private fun stopIfSelected(process: ProcessDescriptor) {
    if (process == selectedProcess) {
      val deadDescriptor =
        object : ProcessDescriptor by process {
          override val isRunning = false
        }
      // Even though we're adding the dead process to the list of processes, leave the live one in
      // there too. That way, a user can select the running version again, prompting a reconnect.
      _processes.add(deadDescriptor)
      selectedProcess = deadDescriptor
    }
  }

  /** Stop this model, which means we terminate the selected process if it is set. */
  fun stop() {
    synchronized(lock) { selectedProcess?.let { process -> stopIfSelected(process) } }
  }
}
