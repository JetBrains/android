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
package com.android.tools.idea.appinspection.test

import com.android.tools.idea.appinspection.api.process.ProcessDiscovery
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import java.util.concurrent.Executor

class TestProcessDiscovery : ProcessDiscovery {
  private val listeners = mutableMapOf<ProcessListener, Executor>()
  override val devices = mutableListOf<DeviceDescriptor>()

  override fun addProcessListener(executor: Executor, listener: ProcessListener) {
    listeners[listener] = executor
  }
  override fun removeProcessListener(listener: ProcessListener) {
    listeners.remove(listener)
  }

  fun addDevice(device: DeviceDescriptor) = devices.add(device)
  fun removeDevice(device: DeviceDescriptor) = devices.remove(device)
  fun fireConnected(process: ProcessDescriptor) = fire { listener ->
    listener.onProcessConnected(process)
  }
  fun fireDisconnected(process: ProcessDescriptor) = fire { listener ->
    listener.onProcessDisconnected(process)
  }
  private fun fire(block: (ProcessListener) -> Unit) {
    listeners.forEach { (listener, executor) -> executor.execute { block(listener) } }
  }
}
