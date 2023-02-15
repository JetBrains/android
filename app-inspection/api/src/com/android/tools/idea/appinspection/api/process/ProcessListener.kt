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

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor

/**
 * Defines a listener that is fired when a process is available or an existing one is disconnected.
 */
interface ProcessListener {
  /** Subscribers can use this to filter processes they don't care about. */
  val filter: (ProcessDescriptor) -> Boolean

  /** Called when a new process on device is available. */
  fun onProcessConnected(process: ProcessDescriptor)

  /** Called when an existing process is disconnected. */
  fun onProcessDisconnected(process: ProcessDescriptor)
}

/** Simple listener that accepts all processes found by discovery. */
abstract class SimpleProcessListener : ProcessListener {
  final override val filter: (ProcessDescriptor) -> Boolean = { _ -> true }
}
