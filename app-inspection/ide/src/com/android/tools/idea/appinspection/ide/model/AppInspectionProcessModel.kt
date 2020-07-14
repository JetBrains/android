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
 * Additionally, [selectedProcess] is thread-safe to set.
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

  @GuardedBy("lock")
  var selectedProcess: ProcessDescriptor? = null
    set(value) {
      synchronized(lock) {
        if (field != value) {
          field = value
          selectedProcessListeners.forEach { (listener, executor) -> executor.execute(listener) }
        }
      }
    }

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
          selectedProcess = descriptor
        }
      }
    }

    override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      synchronized(lock) {
        _processes.remove(descriptor)
        if (descriptor == selectedProcess) {
          selectedProcess = null
        }
      }
    }
  }

  init {
    processNotifier.addProcessListener(executor, processListener)
  }

  override fun dispose() {
    processNotifier.removeProcessListener(processListener)
  }

  fun isProcessPreferred(processDescriptor: ProcessDescriptor?) =
    processDescriptor != null && getPreferredProcessNames().contains(processDescriptor.processName)
}
