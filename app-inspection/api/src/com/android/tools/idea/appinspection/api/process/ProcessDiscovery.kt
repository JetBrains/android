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

import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import java.util.concurrent.Executor

/**
 * Defines a listener that is fired when a new process becomes available or an existing one is
 * disconnected.
 */
interface ProcessDiscovery {
  /** Returns a list of connected devices aware by the discovery layer. */
  val devices: List<DeviceDescriptor>

  /**
   * Adds a [ProcessListener] to this notifier. The [listener] will receive future connections when
   * they come online, triggered via the passed in [executor].
   */
  fun addProcessListener(executor: Executor, listener: ProcessListener)

  /** Removes a [ProcessListener] previously registered with [addProcessListener]. */
  fun removeProcessListener(listener: ProcessListener)
}
