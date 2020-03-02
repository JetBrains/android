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
package com.android.tools.idea.layoutinspector.transport

import com.android.tools.idea.util.ListenerCollection
import com.android.tools.profiler.proto.Common

/**
 * A process manager that keeps track of the available processes for the Layout Inspector.
 */
interface InspectorProcessManager {

  /**
   * Returns a sequence of the known devices.
   */
  fun getStreams(): Sequence<Common.Stream>

  /**
   * Returns a sequence of the known processes for the specified device/stream.
   */
  fun getProcesses(stream: Common.Stream): Sequence<Common.Process>

  /**
   * Listeners for changes to the data in this process manager.
   */
  val processListeners: ListenerCollection<() -> Unit>

  /**
   * Returns true if the specified process is still known to exist.
   */
  fun isProcessActive(stream: Common.Stream, process: Common.Process): Boolean
}
