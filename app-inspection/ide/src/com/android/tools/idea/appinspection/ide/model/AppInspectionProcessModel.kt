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
package com.android.tools.idea.appinspection.ide.model

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.api.process.ProcessNotifier
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Executor

/**
 * Model class that owns a list of active [ProcessDescriptor] targets with listeners that trigger
 * when one is added or removed.
 *
 * The constructor takes an [executor] which gives it affinity to a particular thread (defaulting
 * to the current thread mainly for testing, but in production, an EDT executor is more likely to
 * be useful for UI-related work). This executor will be used to respond to external process updates.
 *
 * Additionally, [selectedProcess] offers a thread-safe way to set and get the currently selected
 * process in app inspection view. The setting of [selectedProcess] triggers the refreshing of
 * inspector tabs in the tool window.
 *
 * Stopping and starting inspection is supported. When inspection is stopped, this model will treat
 * the currently selected process (if it exists) as disconnected and perform the appropriate cleanup.
 * Then it will stop reacting to non-user originated settings of [selectedProcess]. In other words,
 * inspectors will only be launched when user clicks on a dropdown item, in which case inspection is
 * restarted.
 */
class AppInspectionProcessModel(private val executor: Executor,
                                private val processNotifier: ProcessNotifier,
                                private val getPreferredProcessNames: () -> List<String>) : Disposable {

  @TestOnly
  constructor(processNotifier: ProcessNotifier, getPreferredProcessNames: () -> List<String>) :
    this(MoreExecutors.directExecutor(), processNotifier, getPreferredProcessNames)

  private val lock = Any()

  @GuardedBy("lock")
  private val selectedProcessListeners = mutableMapOf<() -> Unit, Executor>()

  @GuardedBy("lock")
  private val _processes = mutableSetOf<ProcessDescriptor>()

  val processes: Set<ProcessDescriptor>
    get() = synchronized(lock) { _processes.toSet() }

  /**
   * This represents the currently selected process in app inspection tool window.
   *
   * It is set in a number of ways:
   *   1) user clicking on an item in the dropdown box
   *   2) as a result of discovering a new process
   */
  @GuardedBy("lock")
  var selectedProcess: ProcessDescriptor? = null
    private set

  /**
   * Sets the currently selected process. This has the side effect of notifying App Inspection View to refresh its UI.
   *
   * This setter does things differently depending on whether Inspection is stopped or not. If stopped, then it will not be set unless the
   * setting is done by user. This prevents the automatic starting of new inspectors by the framework. See class header for more details.
   */
  fun setSelectedProcess(descriptor: ProcessDescriptor?, isUserAction: Boolean = false) {
    synchronized(lock) {
      if (isStopped && !isUserAction) return else isStopped = false
      if (descriptor != selectedProcess) {
        // While we leave processes in the list when they die, once we update the active
        // selection, we silently prune them at that point. Otherwise, dead processes would
        // continue to build up. This also has the nice effect of making it feel that when a
        // user starts running a new process, it neatly replaces the last dead one.
        _processes.removeAll { it != descriptor && !it.isRunning }
        selectedProcess = descriptor
        selectedProcessListeners.forEach { (listener, executor) -> executor.execute(listener) }
      }
    }
  }

  @GuardedBy("lock")
  private var isStopped = false

  /**
   * Add a listener which will be triggered with the selected process when it changes.
   */
  fun addSelectedProcessListeners(executor: Executor, listener: () -> Unit) {
    synchronized(lock) {
      selectedProcessListeners[listener] = executor
    }
  }

  @TestOnly
  fun addSelectedProcessListeners(listener: () -> Unit) = addSelectedProcessListeners(MoreExecutors.directExecutor(), listener)

  private val processListener = object : ProcessListener {
    override fun onProcessConnected(descriptor: ProcessDescriptor) {
      synchronized(lock) {
        _processes.add(descriptor)
        if (isProcessPreferred(descriptor) && !isProcessPreferred(selectedProcess)) {
          setSelectedProcess(descriptor)
        }
      }
    }

    override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      synchronized(lock) {
        _processes.remove(descriptor)
        setOfflineProcess(descriptor)
      }
    }
  }

  init {
    processNotifier.addProcessListener(executor, processListener)
  }

  override fun dispose() {
    processNotifier.removeProcessListener(processListener)
  }

  fun isProcessPreferred(processDescriptor: ProcessDescriptor?, includeDead: Boolean = false): Boolean {
    return processDescriptor != null
           && (processDescriptor.isRunning || includeDead)
           && getPreferredProcessNames().contains(processDescriptor.processName)
  }

  @GuardedBy("lock")
  private fun setOfflineProcess(descriptor: ProcessDescriptor) {
    val deadDescriptor = object : ProcessDescriptor by descriptor {
      override val isRunning = false
    }
    _processes.add(deadDescriptor)
    if (descriptor == selectedProcess) {
      setSelectedProcess(deadDescriptor)
    }
  }

  fun stopInspection(descriptor: ProcessDescriptor) {
    if (descriptor != selectedProcess) return
    synchronized(lock) {
      setOfflineProcess(descriptor)
      isStopped = true
    }
  }
}
